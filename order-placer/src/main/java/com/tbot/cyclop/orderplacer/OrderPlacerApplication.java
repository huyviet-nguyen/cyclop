package com.tbot.cyclop.orderplacer;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.exception.OpenOrderFailException;
import com.tbot.cyclop.orderplacer.repo.*;
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

import java.util.*;
import java.util.function.Function;

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
    public BotRepo botRepo;

    @Value("${bot.strategy.refreshRate}")
    public long STRATEGY_REFRESH_RATE;

    private final HashMap<String, Double> candlePriceMap = new HashMap<>();
    private final HashMap<String, Double> candlePumpMap = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(OrderPlacerApplication.class);

    private final HashMap<String, Order> orderCache = new HashMap<>();

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, Order>> process() {
        return stringKlineDataKStream -> stringKlineDataKStream.flatMapValues(
                (key, value) ->
                {
                    long lag = System.currentTimeMillis() - value.getCandleTimestamp();
                    if (lag > 5000) {
                        logger.warn("HIGH LAG : {} -> IGNORED!", lag);
                        return new ArrayList<>();
                    }
                    String positionSide = value.getCurrentPrice() < value.getOpenPrice() ? "LONG" : "SHORT";
                    String symbolString = value.getSymbol().replace("USDT", "_USDT");
                    Flux<Strategy> strategyFlux = strategyRepo.findByCandleStickAndSymbolStringAndPositionSideAndStatus("M".concat(value.getInterval()), symbolString, positionSide, "ACTIVE");
                    Flux<Order> orderAckFlux = strategyFlux
                            .filter(strategy -> strategy.getBot().getStatus().equals("RUNNING"))
                            .publishOn(Schedulers.boundedElastic()).mapNotNull(
                                    (Strategy strategy) ->
                                    {
                                        try {
                                            assert strategy != null;
                                            logger.info("PROCESS | SYMBOL: {} | STRATEGY: {} | BOT: {}", strategy.getSymbolString(), strategy.toNotiString(), strategy.getBot().getName());
                                            String mapKey = value.getSymbol().concat(".").concat(value.getInterval());
                                            double openPrice = candlePriceMap.get(mapKey) == null ? 0 : candlePriceMap.get(mapKey);
                                            boolean isNewCandle = value.getOpenPrice() != openPrice;
                                            if (isNewCandle) {
                                                candlePumpMap.put(mapKey, calculateChangePercent(openPrice, value.getOpenPrice()));
                                                candlePriceMap.put(mapKey, value.getOpenPrice());
                                                logger.info("NEW CANDLE STARTED | OPEN PRICE {} | LAST PUMP {}", candlePriceMap.get(mapKey), candlePumpMap.get(mapKey));
                                            }
                                            double changePercent = calculateChangePercent(value.getOpenPrice(), value.getCurrentPrice());
                                            double ignorePercent = calculateNewValue(candlePumpMap.get(mapKey), strategy.getIgnore());
                                            if (Math.abs(changePercent) < ignorePercent) {
                                                logger.info("STRATEGY {} | IGNORED | {} < {}% OF LAST PUMP {}", strategy.toNotiString(), Math.abs(changePercent), ignorePercent, candlePumpMap.get(mapKey));
                                                return null;
                                            }
                                            Order latestOrder = orderCache.get(strategy.getId());
                                            if (latestOrder == null) {
                                                if (canSubmit(strategy, value)) {
                                                    logger.info("ORDER CAN BE SUBMIT | CURRENT CHANGE {} | OC {} | EXTEND {}", changePercent, strategy.getOrderChange(), strategy.getExtendOrderChangePercent());
                                                    Order submitOrder = submitOrder(value, strategy);
                                                    if (submitOrder != null) return submitOrder;
                                                }
                                            } else {
                                                logger.info("STRATEGY {} | LAST ORDER ID {} | STATUS {}", strategy.toNotiString(), latestOrder.getId(), latestOrder.getOrderStatus());
                                                OrderStatus oldStatus = latestOrder.getOrderStatus();
                                                Order order = orderPlacerService.handleSyncStatus(value, latestOrder, strategy);
                                                OrderStatus newStatus = order.getOrderStatus();
                                                boolean statusChanged = !oldStatus.equals(order.getOrderStatus());
                                                boolean orderMatchCandle = value.getOpenPrice() == order.getCandleOpenPrice();
                                                logger.info("STRATEGY {} | LAST ORDER ID {} | STATUS AFTER SYNCED {}", strategy.toNotiString(), order.getId(), order.getOrderStatus());
                                                if (!statusChanged) {
                                                    if (!newStatus.equals(OrderStatus.OPEN) && !orderMatchCandle) {
                                                        orderCache.put(strategy.getId(), null);
                                                        orderPlacerService.handleCancelOrder(order, strategy);
                                                        return null;
                                                    }
                                                    if (newStatus.equals(OrderStatus.OPEN) && !orderMatchCandle) {
                                                        orderPlacerService.handleReduceTakeProfit(strategy, value, order);
                                                        return null;
                                                    }
                                                } else {
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
                                                            orderCache.put(strategy.getId(), null);
                                                            decorateNotification(order, strategy);
                                                            return order;
                                                        }
                                                        case OrderStatus.IGNORED -> {
                                                            orderCache.put(strategy.getId(), null);
                                                            return null;
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            logger.error(e.getMessage());
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
            if (submitOrder.getOrderStatus().equals(OrderStatus.SUBMIT)) {
                orderCache.put(strategy.getId(), submitOrder);
                return submitOrder;
            }
        } catch (OpenOrderFailException e) {
            notificationService.sendErrorNotification(strategy, value, e.getMessage());
        }
        return null;
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
