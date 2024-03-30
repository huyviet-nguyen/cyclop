package com.tbot.cyclop.orderplacer;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
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
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.function.Function;

import static com.tbot.cyclop.orderplacer.util.PercentageUtil.calculateChangePercent;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.calculateNewValue;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;


@SpringBootApplication
@EnableCaching
public class OrderPlacerApplication {

    @Autowired
    public StrategyRepo strategyRepo;

    @Autowired
    public OrderRepo orderRepo;

    @Autowired
    public NotificationService notificationService;

    @Autowired
    public OrderPlacerService orderPlacerService;

    @Value("${bot.strategy.refreshRate}")
    public long STRATEGY_REFRESH_RATE;
    private final HashMap<String, Double> candlePriceMap = new HashMap<>();

    private final HashMap<String, Double> candlePumpMap = new HashMap<>();

    private final Logger logger = LoggerFactory.getLogger(OrderPlacerApplication.class);

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, Order>> process() {
        return stringKlineDataKStream -> stringKlineDataKStream.flatMapValues(
                (key, value) ->
                {
                    logger.info("LAG : {}", System.currentTimeMillis() - value.getTimestamp());
                    String positionSide = value.getCurrentPrice() >= value.getOpenPrice() ? "LONG" : "SHORT";
                    String symbolString = value.getSymbol().replace("USDT", "_USDT");
                    Flux<Strategy> strategyFlux = strategyRepo.findByCandleStickAndSymbolStringAndPositionSideAndStatus("M".concat(value.getInterval()), symbolString, positionSide, "ACTIVE");
                    Flux<Order> orderAckFlux = strategyFlux.publishOn(Schedulers.boundedElastic()).mapNotNull(
                            (Strategy strategy) ->
                            {
                                try {
                                    assert strategy != null;
                                    logger.info("<============| START PROCESS WITH | SYMBOL: {} | STRATEGY {}  | POSITION {}", strategy.getSymbolString(), strategy.toNotiString(), strategy.getPositionSide());
                                    String mapKey = value.getSymbol().concat(".").concat(value.getInterval());
                                    candlePriceMap.computeIfAbsent(mapKey, v -> value.getOpenPrice());
                                    candlePumpMap.computeIfAbsent(mapKey, v -> (double) 0L);

                                    //handle update candle open
                                    double openPrice = candlePriceMap.get(mapKey);
                                    boolean isNewCandle = value.getOpenPrice() != openPrice;
                                    if (isNewCandle) {
                                        candlePumpMap.put(mapKey, calculateChangePercent(candlePriceMap.get(mapKey), value.getOpenPrice()));
                                        candlePriceMap.put(key, value.getOpenPrice());
                                    }
                                    // handle ignore
                                    double changePercent = calculateChangePercent(value.getOpenPrice(), value.getCurrentPrice());
                                    double ignorePercent = calculateNewValue(candlePriceMap.get(mapKey), strategy.getIgnore());
                                    if (Math.abs(changePercent) < ignorePercent) {
                                        return null;
                                    }


                                    Order latestOrder = strategy.getLatestOrder();
                                    if (latestOrder == null) {
                                        if (canSubmit(strategy, value)) {
                                            Order submitOrder = submitOrder(value, strategy);
                                            if (submitOrder != null) return submitOrder;
                                        }
                                    } else {
                                        OrderStatus oldStatus = latestOrder.getOrderStatus();
                                        Order order = orderPlacerService.handleSyncStatus(value, latestOrder, strategy);
                                        if (!order.getOrderStatus().equals(oldStatus)) {
                                            return decorateNotification(order, strategy);
                                        }
                                        if (isNewCandle && latestOrder.getOrderStatus().equals(OrderStatus.OPEN)) {
                                            return orderPlacerService.handleReduceTakeProfit(strategy, value, latestOrder);
                                        }
                                        switch (latestOrder.getOrderStatus()) {
                                            case SYS_CREATED, TOOK_PROFIT, STOPPED_LOSS, MISSED, CLOSED_UNKNOWN -> {
                                                if (canSubmit(strategy, value)) {
                                                    Order submitOrder = submitOrder(value, strategy);
                                                    if (submitOrder != null) return submitOrder;
                                                }
                                            }
                                        }

                                        if (isNewCandle && order.getOrderStatus().equals(OrderStatus.SUBMIT)) {
                                            strategy.setLatestOrder(null);
                                            strategyRepo.save(strategy).block();
                                            return orderPlacerService.handleCancelOrder(latestOrder, strategy);
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.error(e.getMessage());
                                }
                                return null;
                            }
                    );
                    return orderAckFlux.filter(order -> !order.getOrderStatus().equals(OrderStatus.SYS_CREATED)).mapNotNull(order -> {
                        if (order.getOrderStatus().equals(OrderStatus.CANCELED)) {
                            orderRepo.delete(order).block();
                            return null;
                        } else {
                            return orderRepo.save(order).block();
                        }
                    }).toIterable();
                }
        );
    }

    @Nullable
    protected Order submitOrder(KlineData value, Strategy strategy) throws Exception {
        Order submitOrder = orderPlacerService.handleSubmitOrder(strategy, value);
        if (submitOrder.getOrderStatus().equals(OrderStatus.SUBMIT)) {
            Order newOrder = orderRepo.save(submitOrder).block();
            strategy.setLatestOrder(newOrder);
            strategyRepo.save(strategy).block();
            return submitOrder;
        }
        return null;
    }

    public Order decorateNotification(Order order, Strategy strategy) {
        try {
            if (order != null) {
                notificationService.sendNotification(order, strategy);
            }
        } catch (Exception e) {
            logger.error("CANNOT SEND NOTIFICATION");
        }
        return order;
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderPlacerApplication.class, args);
    }
}
