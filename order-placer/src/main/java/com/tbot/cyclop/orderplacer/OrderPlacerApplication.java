package com.tbot.cyclop.orderplacer;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.exception.OpenOrderFailException;
import com.tbot.cyclop.orderplacer.repo.*;
import com.tbot.cyclop.orderplacer.service.MarketContextHolder;
import com.tbot.cyclop.orderplacer.service.OrderPlacerService;
import com.tbot.cyclop.orderplacer.service.NotificationService;
import org.apache.kafka.streams.kstream.KStream;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.exceptionToString;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.calculateChangePercent;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.calculateNewValue;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;


@SpringBootApplication
public class OrderPlacerApplication {

    @Autowired
    public StrategyRepo strategyRepo;


    @Autowired
    public NotificationService notificationService;

    @Autowired
    public OrderPlacerService orderPlacerService;

    @Autowired
    public MarketContextHolder marketContextHolder;

    @Autowired
    public BotRepo botRepo;

    @Autowired
    public OrderRepo orderRepo;

    @Autowired
    public ErrorTraceRepo errorTraceRepo;

    @Value("${bot.strategy.refreshRate}")
    public long STRATEGY_REFRESH_RATE;
    private final Logger logger = LoggerFactory.getLogger(OrderPlacerApplication.class);

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, Order>> process() {
        return stringKlineDataKStream -> stringKlineDataKStream.flatMapValues(
                (key, value) ->
                {
                    long lag = System.currentTimeMillis() - value.getCandleTimestamp();
                    if (lag > 2000) {
                        logger.warn("HIGH LAG : {} -> IGNORED!", lag);
                        return new ArrayList<>();
                    }
                    String symbolString = value.getSymbol().replace("USDT", "_USDT");
                    Flux<Strategy> strategyFlux = strategyRepo.findBySymbolStringAndCandleStickAndStatus(symbolString, "M".concat(value.getInterval()), "ACTIVE");
                    Flux<Order> orderAckFlux = strategyFlux
                            .filter(strategy -> strategy.getBot() != null && strategy.getBot().getStatus().equals("RUNNING"))
                            .publishOn(Schedulers.boundedElastic()).mapNotNull(
                                    (Strategy strategy) ->
                                    {
                                        try {
                                            assert strategy != null;
                                            String strategyNotiString = strategy.toNotiString();
                                            String mapKey = value.getSymbol().concat(".").concat(value.getInterval());
                                            double openPrice = marketContextHolder.getCandleOpenPrice(mapKey);
                                            boolean isNewCandle = value.getOpenPrice() != openPrice;
                                            if (isNewCandle) {
                                                marketContextHolder.updateCandleMaps(mapKey, value.getOpenPrice(), value.getCurrentPrice());
                                                logger.info("{} | NEW CANDLE STARTED | OPEN PRICE {} | LAST PUMP {}", mapKey, marketContextHolder.getCandleOpenPrice(mapKey), marketContextHolder.getCandlePump(mapKey));
                                            }
                                            double changePercent = calculateChangePercent(value.getOpenPrice(), value.getCurrentPrice());
                                            double ignorePercent = calculateNewValue(marketContextHolder.getCandlePump(mapKey), strategy.getIgnore());
                                            if (Math.abs(changePercent) < ignorePercent) {
                                                return null;
                                            }
                                            Order latestOrder = marketContextHolder.getOrder(strategy.getId());
                                            if (latestOrder == null) {
                                                if (canSubmit(strategy, value)) {
                                                    logger.info("ORDER CAN BE SUBMIT | CURRENT CHANGE {} | OC {} | EXTEND {}", changePercent, strategy.getOrderChange(), strategy.getExtendOrderChangePercent());
                                                    Order submitOrder = submitOrder(value, strategy);
                                                    if (submitOrder != null) {
                                                        return submitOrder;
                                                    }
                                                }
                                            } else {
                                                logger.info("STRATEGY {} | LAST ORDER ID {} | STATUS {}", strategyNotiString, latestOrder.getPlatformOrderId(), latestOrder.getOrderStatus());
                                                OrderStatus oldStatus = latestOrder.getOrderStatus();
                                                Order order = orderPlacerService.handleSyncStatus(value, latestOrder, strategy);
                                                OrderStatus newStatus = order.getOrderStatus();
                                                boolean orderMatchCandle = value.getOpenPrice() == order.getCandleOpenPrice();
                                                if (!orderMatchCandle) {
                                                    if (newStatus.equals(OrderStatus.SUBMIT)) {
                                                        marketContextHolder.removeOrder(strategy.getId());
                                                        orderPlacerService.handleCancelOrder(order, strategy);
                                                        return null;
                                                    }
                                                    if (newStatus.equals(OrderStatus.OPEN)) {
                                                        double beforeReduced = order.getCurrentActualTakeProfit();
                                                        try {
                                                            orderPlacerService.handleReduceTakeProfit(strategy, value, order);
                                                            order.setCandleOpenPrice(value.getOpenPrice());
                                                            marketContextHolder.removeOrder(strategy.getId());
                                                            marketContextHolder.cacheOrder(strategy.getId(),order);
                                                        } catch (Exception e) {
                                                            orderPlacerService.handleCancelOrder(order, strategy);
                                                            marketContextHolder.removeOrder(strategy.getId());
                                                            throw e;
                                                        }
                                                        logger.info("REDUCED TAKE PROFIT FOR ORDER {} FROM {} TO {}", order.getPlatformOrderId(), beforeReduced, order.getCurrentActualTakeProfit());
                                                        return null;
                                                    }
                                                }

                                                boolean statusChanged = !oldStatus.equals(order.getOrderStatus());
                                                logger.info("STRATEGY {} | LAST ORDER ID {} | STATUS AFTER SYNCED {}", strategyNotiString, order.getPlatformOrderId(), order.getOrderStatus());

                                                if (statusChanged) {
                                                    switch (newStatus) {
                                                        case OrderStatus.OPEN -> {
                                                            decorateNotification(order, strategy);
                                                            return order;
                                                        }
                                                        case OrderStatus.CLOSED -> {
                                                            if (order.getProfit() > 0) {
                                                                strategy.getBot().win();
                                                            } else if (order.getProfit() < 0) {
                                                                strategy.getBot().lose();
                                                            }
                                                            botRepo.save(strategy.getBot()).block();
                                                            marketContextHolder.removeOrder(strategy.getId());
                                                            decorateNotification(order, strategy);
                                                            return order;
                                                        }
                                                        case OrderStatus.IGNORED -> {
                                                            marketContextHolder.removeOrder(strategy.getId());
                                                            orderPlacerService.handleCancelOrder(order, strategy);
                                                            return null;
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            ErrorTrace trace = new ErrorTrace();
                                            trace.setCreatedAt(LocalDateTime.now());
                                            trace.setStackTrace(exceptionToString(e));
                                            errorTraceRepo.save(trace).block();
                                            notificationService.sendErrorNotification(strategy, value, e.getMessage());
                                        }
                                        return null;
                                    }
                            );
                    return orderAckFlux.toIterable();
                }
        );
    }

    @Nullable
    protected Order submitOrder(KlineData value, Strategy strategy) throws Exception {
        try {
            Order submitOrder = orderPlacerService.handleSubmitOrder(strategy, value);
            marketContextHolder.removeOrder(strategy.getId());
            marketContextHolder.cacheOrder(strategy.getId(), submitOrder);
            return submitOrder;
        } catch (OpenOrderFailException e) {
            logger.error(e.getMessage());
            notificationService.sendErrorNotification(strategy, value, e.getMessage());
            throw e;
        }
    }

    public void decorateNotification(Order order, Strategy strategy) {
        try {
            if (order != null) {
                notificationService.sendNotification(order, strategy);
                if (order.getOrderStatus().equals(OrderStatus.CLOSED)) {
                    int win = strategy.getBot().getWinCount();
                    int loose = strategy.getBot().getLoseCount();
                    notificationService.sendReportNotification(order, strategy, win, loose);
                }
            }
        } catch (Exception e) {
            logger.error("CANNOT SEND NOTIFICATION:");
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderPlacerApplication.class, args);
    }
}
