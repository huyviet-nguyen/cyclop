package com.tbot.cyclop.orderplacer;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.*;
import com.tbot.cyclop.orderplacer.service.OrderPlacerService;
import com.tbot.cyclop.orderplacer.service.NotificationService;
import jakarta.annotation.PostConstruct;
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
                                Order latestOrder = strategy.getLatestOrder();
                                boolean isNewCandle = isNewCandle(strategy, value);
                                if (isNewCandle) {
                                    orderPlacerService.updateCandle(strategy, value);
                                }
                                if (canIgnore(strategy, value)) {
                                    return null;
                                }
                                if (canSubmit(strategy, value)) {
                                    try {
                                        Order order = orderPlacerService.handleSubmitOrder(strategy, value, latestOrder, isNewCandle);
                                        if (order != null) {
                                            strategy.setLatestOrder(orderRepo.save(order).block());
                                            strategyRepo.save(strategy).block();
                                        }
                                        return order;
                                    } catch (Exception e) {
                                        logger.error(e.getMessage());
                                    }
                                }
                                if (latestOrder != null) {
                                    if (canTakeProfit(latestOrder, value) || canStopLoss(latestOrder, value) || canOpen(latestOrder, strategy, value)) {
                                        try {
                                            return decorateNotification(orderPlacerService.handleSyncStatus(value, latestOrder, strategy), strategy);
                                        } catch (Exception e) {
                                            logger.error(e.getMessage());
                                        }
                                    }
                                    if (!canTakeProfit(latestOrder, value) && isNewCandle) {
                                        try {
                                            return decorateNotification(orderPlacerService.handleReduceTakeProfit(strategy, value, latestOrder), strategy);
                                        } catch (Exception e) {
                                            logger.error(e.getMessage());
                                        }
                                    }
                                    return null;
                                } else {
                                    return null;
                                }
                            }
                    );
                    logProcessTime(startProcessTime);
                    return orderRepo.saveAll(orderAckFlux).toIterable();
                }
        );
    }

    private void logProcessTime(long startTime) {
        long doneProcessTime = System.currentTimeMillis();
        if (doneProcessTime - startTime > 10) {
            logger.warn("LONG PROCESS : {} ms", doneProcessTime - startTime);
        }
    }

    @PostConstruct
    public void populateSymbolString() {
        strategyRepo.saveAll(strategyRepo.findAll().map(strategy -> {
            if (strategy.getSymbol() != null) {
                strategy.setSymbolString(strategy.getSymbol().getSymbol());
            }
            return strategy;
        }).toIterable()).subscribe();
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
