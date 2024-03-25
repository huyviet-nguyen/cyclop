package com.tbot.cyclop.orderplacer;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.*;
import com.tbot.cyclop.orderplacer.service.OrderPlacerService;
import com.tbot.cyclop.orderplacer.service.NotificationService;
import jakarta.annotation.PostConstruct;
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

    @Value("${bot.strategy.refreshRate}")
    public long STRATEGY_REFRESH_RATE;
    private final Logger logger = LoggerFactory.getLogger(OrderPlacerApplication.class);

    private final Map<String, Set<String>> ACTIVE_WATCH_LIST = new HashMap<>();

    private long WATCH_LIST_UPDATED_ON;

    @PostConstruct
    public void initWatchList() {
        ACTIVE_WATCH_LIST.clear();
        strategyRepo.findAllByStatus("ACTIVE").subscribe(strategy -> {
            String watch = String.join(".", strategy.getPlatform(), strategy.getSymbolString().replace("_", ""), strategy.getCandleStick().replace("M", ""), strategy.getPositionSide());
            ACTIVE_WATCH_LIST.computeIfAbsent(watch, k -> new HashSet<>());
            ACTIVE_WATCH_LIST.get(watch).add(strategy.getId());
        });
        WATCH_LIST_UPDATED_ON = System.currentTimeMillis();
    }

    private void maintainWatchList() {
        if (System.currentTimeMillis() - WATCH_LIST_UPDATED_ON > STRATEGY_REFRESH_RATE) {
            initWatchList();
        }
    }

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, Order>> process() {
        return stringKlineDataKStream -> stringKlineDataKStream.flatMapValues(
                (key, value) ->
                {
                    logger.debug("LAG : {}", System.currentTimeMillis() - value.getTimestamp());
                    maintainWatchList();
                    String positionSide = value.getCurrentPrice() >= value.getOpenPrice() ? "LONG" : "SHORT";
                    if (!ACTIVE_WATCH_LIST.containsKey(key.concat(".").concat(positionSide))) {
                        return Collections.EMPTY_LIST;
                    }
                    Flux<Strategy> strategyFlux = strategyRepo.findAllById(ACTIVE_WATCH_LIST.get(key.concat(".").concat(positionSide)));
                    Flux<Order> orderAckFlux = strategyFlux.publishOn(Schedulers.boundedElastic()).mapNotNull(
                            (Strategy s) ->
                            {
                                try {
                                    Strategy strategy = strategyRepo.findById(s.getId()).block();
                                    assert strategy != null;
                                    logger.info("<============| START PROCESS WITH | SYMBOL: {} | STRATEGY {}  | POSITION {}", strategy.getSymbolString(), strategy.toNotiString(), strategy.getPositionSide());
                                    boolean isNewCandle = isNewCandle(strategy, value);
                                    if (isNewCandle) {
                                        orderPlacerService.updateCandle(strategy, value);
                                    }
                                    if (canIgnore(strategy, value)) {
                                        return null;
                                    }
                                    Order latestOrder = strategy.getLatestOrder();
                                    if (latestOrder == null) {
                                        Order submitOrder = submitOrder(value, strategy);
                                        if (submitOrder != null) return submitOrder;
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
    private Order submitOrder(KlineData value, Strategy strategy) throws Exception {
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
