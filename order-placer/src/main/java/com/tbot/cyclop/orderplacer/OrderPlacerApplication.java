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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
    private CacheManager cacheManager;

    @Autowired
    public NotificationService notificationService;

    @Autowired
    public OrderPlacerService orderPlacerService;

    @Autowired
    public BotRepo botRepo;

    @Value("${bot.strategy.refreshRate}")
    public long STRATEGY_REFRESH_RATE;

    private static final String STRATEGY_CACHE_NAME = "strategyCache";
    private volatile long lastClearCache = System.currentTimeMillis();
    private final HashMap<String, Double> candlePriceMap = new HashMap<>();
    private final HashMap<String, Double> candlePumpMap = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(OrderPlacerApplication.class);

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, Order>> process() {
        return stringKlineDataKStream -> stringKlineDataKStream.flatMapValues(
                (key, value) ->
                {
                    long lag = System.currentTimeMillis() - value.getTimestamp();
                    if (lag > 10000) {
                        logger.warn("HIGH LAG : {} -> IGNORED!", lag);
                        return new ArrayList<>();
                    }
                    String positionSide = value.getCurrentPrice() >= value.getOpenPrice() ? "LONG" : "SHORT";
                    String symbolString = value.getSymbol().replace("USDT", "_USDT");
                    maintainCache();
                    Flux<Strategy> strategyFlux = strategyRepo.findByCandleStickAndSymbolStringAndPositionSideAndStatus("M".concat(value.getInterval()), symbolString, positionSide, "ACTIVE");
                    Flux<Order> orderAckFlux = strategyFlux
                            .filter(strategy -> strategy.getBot().getStatus().equals("RUNNING"))
                            .publishOn(Schedulers.boundedElastic()).mapNotNull(
                            (Strategy strategy) ->
                            {
                                try {
                                    assert strategy != null;
                                    logger.info("<============| START PROCESS WITH | SYMBOL: {} | STRATEGY {}  | POSITION {}", strategy.getSymbolString(), strategy.toNotiString(), strategy.getPositionSide());
                                    String mapKey = value.getSymbol().concat(".").concat(value.getInterval());
                                    double openPrice = candlePriceMap.get(mapKey) == null ? 0 : candlePriceMap.get(mapKey);
                                    boolean isNewCandle = value.getOpenPrice() != openPrice;
                                    if (isNewCandle) {
                                        candlePriceMap.put(mapKey, value.getOpenPrice());
                                        candlePumpMap.put(mapKey, calculateChangePercent(candlePriceMap.get(mapKey), value.getOpenPrice()));
                                    }
                                    // handle ignore
                                    double changePercent = calculateChangePercent(value.getOpenPrice(), value.getCurrentPrice());
                                    double ignorePercent = calculateNewValue(candlePumpMap.get(mapKey), strategy.getIgnore());
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
                                            switch (order.getOrderStatus()) {
                                                case OrderStatus.TOOK_PROFIT -> strategy.getBot().win();
                                                case OrderStatus.STOPPED_LOSS -> strategy.getBot().lose();
                                            }
                                            botRepo.save(strategy.getBot()).block();
                                            return decorateNotification(order, strategy);
                                        }
                                        if (isNewCandle && order.getOrderStatus().equals(OrderStatus.OPEN)) {
                                            return orderPlacerService.handleReduceTakeProfit(strategy, value, latestOrder);
                                        }
                                        switch (order.getOrderStatus()) {
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
        try {
            Order submitOrder = orderPlacerService.handleSubmitOrder(strategy, value);
            if (submitOrder.getOrderStatus().equals(OrderStatus.SUBMIT)) {
                Order newOrder = orderRepo.save(submitOrder).block();
                strategy.setLatestOrder(newOrder);
                strategyRepo.save(strategy).block();
                return submitOrder;
            }
        } catch (OpenOrderFailException e) {
            notificationService.sendErrorNotification(strategy, value, e.getMessage());
        }
        return null;
    }

    private void maintainCache() {
        if (System.currentTimeMillis() - lastClearCache > 120000) {
            evictCache(STRATEGY_CACHE_NAME);
            lastClearCache = System.currentTimeMillis();
        }
    }

    public Order decorateNotification(Order order, Strategy strategy) {
        try {
            if (order != null) {
                notificationService.sendNotification(order, strategy);
                if (order.getOrderStatus().equals(OrderStatus.TOOK_PROFIT) || order.getOrderStatus().equals(OrderStatus.STOPPED_LOSS)) {
                    int win = strategy.getBot().getWinCount();
                    int loose = strategy.getBot().getLoseCount();
                    notificationService.sendReportNotification(order, strategy, win, loose);
                }
            }
        } catch (Exception e) {
            logger.error("CANNOT SEND NOTIFICATION:");
            System.out.println(e.getMessage());
        }
        return order;
    }

    public void evictCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderPlacerApplication.class, args);
    }
}
