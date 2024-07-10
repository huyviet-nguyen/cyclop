package com.tbot.cyclop.orderplacer.function;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.ErrorTrace;
import com.tbot.cyclop.Cyclop.model.Order;
import com.tbot.cyclop.Cyclop.model.OrderStatus;
import com.tbot.cyclop.Cyclop.model.Strategy;
import com.tbot.cyclop.orderplacer.exception.OpenOrderFailException;
import com.tbot.cyclop.orderplacer.repo.BotRepo;
import com.tbot.cyclop.orderplacer.repo.ErrorTraceRepo;
import com.tbot.cyclop.orderplacer.repo.OrderRepo;
import com.tbot.cyclop.orderplacer.repo.StrategyRepo;
import com.tbot.cyclop.orderplacer.service.MarketContextHolder;
import com.tbot.cyclop.orderplacer.service.NotificationService;
import com.tbot.cyclop.orderplacer.service.OrderPlacerService;
import org.apache.kafka.streams.kstream.KStream;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.function.Function;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.exceptionToString;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.*;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.canSubmit;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.getMapKey;

@Component
public class OrderPlacingStreamFunction {

    private final StrategyRepo strategyRepo;

    private final NotificationService notificationService;

    private final OrderPlacerService orderPlacerService;

    private final MarketContextHolder marketContextHolder;

    private final BotRepo botRepo;

    private final ErrorTraceRepo errorTraceRepo;

    private final Logger logger = LoggerFactory.getLogger(OrderPlacingStreamFunction.class);

    public OrderPlacingStreamFunction(StrategyRepo strategyRepo, NotificationService notificationService, OrderPlacerService orderPlacerService, MarketContextHolder marketContextHolder, BotRepo botRepo, OrderRepo orderRepo, ErrorTraceRepo errorTraceRepo) {
        this.strategyRepo = strategyRepo;
        this.notificationService = notificationService;
        this.orderPlacerService = orderPlacerService;
        this.marketContextHolder = marketContextHolder;
        this.botRepo = botRepo;
        this.errorTraceRepo = errorTraceRepo;
    }

    private boolean isHighLag(KlineData value) {
        long allowedLag = 5000;
        long lag = System.currentTimeMillis() - value.getCandleTimestamp();
        if (lag > allowedLag) {
            logger.warn("HIGH LAG : {} -> IGNORED!", lag);
        }
        return lag > allowedLag;
    }

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, Order>> process() {
        return stringKlineDataKStream -> stringKlineDataKStream.flatMapValues(
                (key, value) ->
                {
                    if (isHighLag(value)) {
                        return new ArrayList<>();
                    }

                    String mapKey = getMapKey(value);
                    marketContextHolder.updateCandleMaxDiff(mapKey, value.getCurrentPrice());
                    String symbolString;
                    if (value.getSourcePlatform().equalsIgnoreCase("mexc")) {
                        symbolString = value.getSymbol().replace("USDT", "_USDT");
                    } else {
                        symbolString = value.getSymbol();
                    }
                    Flux<Strategy> relevantStrategies = strategyRepo
                            .findBySymbolStringAndCandleStickAndStatus(symbolString, "M".concat(value.getInterval()), "ACTIVE");

                    Flux<Order> orderAckFlux = relevantStrategies
                            .publishOn(Schedulers.boundedElastic()).mapNotNull(
                                    (Strategy strategy) ->
                                    {
                                        try {
                                            double lastPump = marketContextHolder.getCandlePump(mapKey);
                                            double lastCandleOpenPrice = marketContextHolder.getCandleOpenPrice(mapKey);

                                            if (isNewCandle(value)) {
                                                marketContextHolder.updateCandleMaps(mapKey, value.getOpenPrice(), value.getCurrentPrice());
                                                logger.info("{} | NEW CANDLE STARTED | OPEN PRICE {} | LAST PUMP {}", mapKey, marketContextHolder.getCandleOpenPrice(mapKey), marketContextHolder.getCandlePump(mapKey));
                                            }
                                            double ignoreAmount = marketContextHolder.getCandleMaxDiff(mapKey) * strategy.getIgnore() / 100;
                                            double openPriceAfterIgnore = strategy.getPositionSide().equals("SHORT") ? value.getOpenPrice() + ignoreAmount : value.getOpenPrice() - ignoreAmount;
                                            double changePercent = calculateChangePercent(openPriceAfterIgnore, value.getCurrentPrice());
                                            double ignorePercent = calculateNewValue(marketContextHolder.getCandlePump(mapKey), strategy.getIgnore());
                                            Order orderBeforeSync = marketContextHolder.getOrder(strategy.getId());
                                            boolean invertedCandle = lastPump * changePercent < 0;
                                            boolean previousCandleMatched = marketContextHolder.getLastOrderCandleOpenPrice(mapKey) == lastCandleOpenPrice;
                                            boolean priceNotSatisfied = Math.abs(changePercent) < Math.abs(ignorePercent);

                                            boolean ignoredByInvertedCandleAndPreviousMatch = invertedCandle && previousCandleMatched && !priceNotSatisfied;

                                            if (orderBeforeSync == null) {
                                                if (invertedCandle && previousCandleMatched && priceNotSatisfied) {
                                                    return null;
                                                }
                                                return handleNullOrderCache(value, strategy, changePercent, ignoredByInvertedCandleAndPreviousMatch);
                                            } else {
                                                return handleExistingOrderCache(value, strategy, orderBeforeSync);
                                            }
                                        } catch (Exception ignored) {
                                            ErrorTrace trace = new ErrorTrace();
                                            trace.setCreatedAt(LocalDateTime.now());
                                            trace.setStackTrace(exceptionToString(ignored));
                                            errorTraceRepo.save(trace).block();
                                            notificationService.sendErrorNotification(strategy, value, ignored.getMessage());
                                        }
                                        return null;
                                    }
                            );
                    return orderAckFlux.toIterable();
                }
        );
    }

    private Order handleNullOrderCache(KlineData value, Strategy strategy, double changePercent, boolean partialIgnoreFlag) throws Exception {
        double maxDiffAbs = marketContextHolder.getCandleMaxDiff(getMapKey(value));
        if (canSubmit(strategy, value, maxDiffAbs, partialIgnoreFlag)) {
            logger.info("ORDER CAN BE SUBMIT | CURRENT CHANGE {} | OC {} | EXTEND {}", changePercent, strategy.getOrderChange(), strategy.getExtendOrderChangePercent());
            return submitOrder(value, strategy, partialIgnoreFlag);
        }
        return null;
    }

    private Order handleExistingOrderCache(KlineData value, Strategy strategy, Order orderBeforeSync) throws IOException, InterruptedException, URISyntaxException {
        String notiString = strategy.toNotiString();
        logger.info("STRATEGY {} | LAST ORDER ID {} | STATUS {}", notiString, orderBeforeSync.getPlatformOrderId(), orderBeforeSync.getOrderStatus());
        OrderStatus statusBeforeSync = orderBeforeSync.getOrderStatus();
        Order orderAfterSync = orderPlacerService.handleSyncStatus(value, orderBeforeSync, strategy);
        OrderStatus statusAfterSync = orderAfterSync.getOrderStatus();
        try {
            if (!orderIsWithinCandle(value, orderAfterSync)) {
                if (statusAfterSync.equals(OrderStatus.SUBMIT)) {
                    marketContextHolder.removeOrder(strategy.getId());
                    orderPlacerService.handleCancelOrder(orderAfterSync, strategy);
                    return null;
                }
                if (statusAfterSync.equals(OrderStatus.OPEN)) {
                    double beforeReduced = orderAfterSync.getCurrentActualTakeProfit();
                    orderPlacerService.handleReduceTakeProfit(strategy, value, orderAfterSync);
                    orderAfterSync.setCandleOpenPrice(value.getOpenPrice());
                    marketContextHolder.removeOrder(strategy.getId());
                    marketContextHolder.cacheOrder(strategy.getId(), orderAfterSync);
                    logger.info("REDUCED TAKE PROFIT FOR ORDER {} FROM {} TO {}", orderAfterSync.getPlatformOrderId(), beforeReduced, orderAfterSync.getCurrentActualTakeProfit());
                    return null;
                }
                if (statusAfterSync.equals(OrderStatus.REMOVE_CACHE)) {
                    marketContextHolder.removeOrder(strategy.getId());
                }
            }

            boolean statusChanged = !statusBeforeSync.equals(orderAfterSync.getOrderStatus());
            logger.info("STRATEGY {} | LAST ORDER ID {} | STATUS AFTER SYNCED {}", notiString, orderAfterSync.getPlatformOrderId(), orderAfterSync.getOrderStatus());

            if (statusChanged) {
                switch (statusAfterSync) {
                    case OrderStatus.OPEN -> {
                        decorateNotification(orderAfterSync, strategy);
                        return orderAfterSync;
                    }
                    case OrderStatus.CLOSED -> {
                        if (orderAfterSync.getProfit() > 0) {
                            strategy.getBot().win();
                        } else if (orderAfterSync.getProfit() < 0) {
                            strategy.getBot().lose();
                        }
                        botRepo.save(strategy.getBot()).block();
                        marketContextHolder.removeOrder(strategy.getId());
                        decorateNotification(orderAfterSync, strategy);
                        return orderAfterSync;
                    }
                    case OrderStatus.IGNORED -> {
                        marketContextHolder.removeOrder(strategy.getId());
                        orderPlacerService.handleCancelOrder(orderAfterSync, strategy);
                        return null;
                    }
                    default -> {
                    }
                }
            }
        } catch (Exception rethrow) {
            marketContextHolder.removeOrder(strategy.getId());
            notificationService.sendErrorNotification(strategy, value, rethrow.getMessage());
            throw rethrow;
        }
        return null;
    }


    private boolean isNewCandle(KlineData value) {
        double openPrice = marketContextHolder.getCandleOpenPrice(getMapKey(value));
        return value.getOpenPrice() != openPrice;
    }

    private boolean orderIsWithinCandle(KlineData kline, Order order) {
        return kline.getOpenPrice() == order.getCandleOpenPrice();
    }

    @Nullable
    protected Order submitOrder(KlineData value, Strategy strategy, boolean partialIgnoreFlag) throws Exception {
        try {
            Order submitOrder = orderPlacerService.handleSubmitOrder(strategy, value, partialIgnoreFlag);
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
            logger.error(e.getMessage());
        }
    }
}
