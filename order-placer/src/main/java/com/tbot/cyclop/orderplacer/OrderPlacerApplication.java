package com.tbot.cyclop.orderplacer;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.*;
import com.tbot.cyclop.orderplacer.service.OrderPlacerService;
import com.tbot.cyclop.orderplacer.service.NotificationService;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.function.Function;

import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;


@SpringBootApplication
public class OrderPlacerApplication {

    @Autowired
    public StrategyRepo strategyRepo;

    @Autowired
    public OrderRepo orderRepo;

    @Autowired
    public NotificationService notificationService;

    @Autowired
    public OrderPlacerService orderPlacerService;
    private final Logger logger = LoggerFactory.getLogger(OrderPlacerApplication.class);

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, Order>> process() {
        return stringKlineDataKStream -> stringKlineDataKStream.flatMapValues(
                (key, value) ->
                {
                    long startProcessTime = System.currentTimeMillis();
                    String candleStick = addCandleStickPrefix(value.getInterval());
                    String symbolString = replaceUsdtSuffix(value.getSymbol());
                    String positionSide = value.getCurrentPrice() > value.getOpenPrice() ? "LONG" : "SHORT";
                    Flux<Strategy> strategyFlux = strategyRepo.findByCandleStickAndSymbolStringAndPositionSideAndStatus(candleStick, symbolString, positionSide, "ACTIVE");
                    Flux<Order> orderAckFlux = strategyFlux.publishOn(Schedulers.boundedElastic()).mapNotNull(
                            (Strategy strategy) ->
                            {
                                try {
                                    boolean isNewCandle = isNewCandle(strategy, value);
                                    if (isNewCandle) {
                                        orderPlacerService.updateCandle(strategy, value);
                                    }
                                    if (canIgnore(strategy, value)) {
                                        return null;
                                    }
                                    Order latestOrder = strategy.getLatestOrder();
                                    if (latestOrder == null) {
                                        if (canSubmit(strategy, value)) {
                                            Order order = orderRepo.save(orderPlacerService.handleSubmitOrder(strategy, value, isNewCandle)).block();
                                            strategy.setLatestOrder(order);
                                            strategyRepo.save(strategy).block();
                                            return order;
                                        }
                                    } else {
                                        if (isNewCandle && latestOrder.getOrderStatus().equals(OrderStatus.OPEN)) {
                                            orderPlacerService.handleReduceTakeProfit(strategy, value, latestOrder);
                                            return latestOrder;
                                        }
                                        if (isNewCandle && latestOrder.getOrderStatus().equals(OrderStatus.SUBMIT)) {
                                            strategy.setLatestOrder(null);
                                            strategyRepo.save(strategy).block();
                                            return orderPlacerService.handleCancelOrder(latestOrder, strategy);
                                        }
                                        OrderStatus oldStatus = latestOrder.getOrderStatus();
                                        switch (latestOrder.getOrderStatus()) {
                                            case SYS_CREATED, TOOK_PROFIT, STOPPED_LOSS, MISSED, CLOSED_UNKNOWN -> {
                                                if (canSubmit(strategy, value)) {
                                                    Order order = orderRepo.save(orderPlacerService.handleSubmitOrder(strategy, value, isNewCandle)).block();
                                                    strategy.setLatestOrder(order);
                                                    strategyRepo.save(strategy).block();
                                                    return order;
                                                }
                                            }
                                            case SUBMIT -> {
                                                if (canOpen(latestOrder, strategy, value)) {
                                                    Order order = orderPlacerService.handleSyncStatus(value, latestOrder, strategy);
                                                    if (!order.getOrderStatus().equals(oldStatus)) {
                                                        return decorateNotification(order, strategy);
                                                    } else {
                                                        return order;
                                                    }
                                                }
                                            }
                                            case OPEN -> {
                                                if (canTakeProfit(latestOrder, value) || canStopLoss(latestOrder, value)) {
                                                    Order order = orderPlacerService.handleSyncStatus(value, latestOrder, strategy);
                                                    if (!order.getOrderStatus().equals(oldStatus)) {
                                                        return decorateNotification(order, strategy);
                                                    } else {
                                                        return order;
                                                    }
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
                    logProcessTime(startProcessTime);
                    return orderAckFlux.mapNotNull(order -> orderRepo.save(order).block()).toIterable();
                }
        );
    }

    private void logProcessTime(long startTime) {
        long doneProcessTime = System.currentTimeMillis();
        if (doneProcessTime - startTime > 10) {
            logger.warn("LONG PROCESS : {} ms", doneProcessTime - startTime);
        }
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
