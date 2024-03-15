package com.tbot.cyclop.orderplacer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.NotificationPayload;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.*;
import com.tbot.cyclop.orderplacer.service.OrderPlacerService;
import com.tbot.cyclop.orderplacer.service.TelegramService;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.function.Function;

import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;


@SpringBootApplication
public class OrderPlacerApplication {

    @Autowired
    public StrategyRepo strategyRepo;

    @Autowired
    public OrderAckHistoryRepo orderAckHistoryRepo;

    @Autowired
    public BotRepo botRepo;

    @Autowired
    public SymbolRepo symbolRepo;

    @Autowired
    public CandleWindowRepo candleWindowRepo;

    @Autowired
    public StrategyMarkerRepo strategyMarkerRepo;

    @Autowired
    public TelegramService telegramService;

    @Autowired
    public UserRepo userRepo;

    @Autowired
    public OrderPlacerService orderPlacerService;
    private final Logger logger = LoggerFactory.getLogger(OrderPlacerApplication.class);

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, OrderAckHistory>> process() {
        return stringKlineDataKStream -> stringKlineDataKStream.flatMapValues(
                (key, value) ->
                {
                    String candleStick = addCandleStickPrefix(value.getInterval());
                    String symbolString = replaceUsdtSuffix(value.getSymbol());
                    String positionSide = value.getCurrentPrice() > value.getOpenPrice() ? "LONG" : "SHORT";
                    Symbol symbol = symbolRepo.findBySymbolAndPlatform(symbolString, value.getSourcePlatform()).block();
                    if (symbol == null) {
                        return new ArrayList<>();
                    }

                    Flux<Strategy> strategyFlux = strategyRepo.findByCandleStickAndSymbol(candleStick, symbol).filter(strategy -> (strategy.getPositionSide().equals(positionSide) || strategy.getPositionSide().equals("BOTH")) && "ACTIVE".equals(strategy.getStatus()));
                    Flux<OrderAckHistory> orderAckFlux = strategyFlux.publishOn(Schedulers.boundedElastic()).mapNotNull(
                            (Strategy strategy) ->
                            {
                                if (newCandle(strategy, value)) {
                                    orderPlacerService.handleCandleWindow(strategy, value);
                                }
                                if (canIgnore(strategy, value)) {
                                    return null;
                                }
                                if (canEntry(strategy, value)) {
                                    return orderPlacerService.handleOpenOrder(strategy, value);
                                }
                                if (canTakeProfit(strategy, value)) {
                                    return orderPlacerService.handleTakeProfit(strategy, value);
                                }
                                if (mustReduceTakeProfit(strategy, value)) {
                                    return orderPlacerService.handleReduceTakeProfit(strategy, value);
                                }
                                if (canStopLoss(strategy, value)) {
                                    return orderPlacerService.handleStopLoss(strategy, value);
                                }
                                return null;
                            }
                    );
//                    notify(orderAckFlux);
                    return orderAckHistoryRepo.saveAll(orderAckFlux).toIterable();
                }
        );
    }

//    private void notify(Flux<OrderAckHistory> orderAckHistoryFlux) {
//        orderAckHistoryFlux
//                .flatMap(orderAckHistory -> {
//                    if (orderAckHistory != null && orderAckHistory.getStrategy() != null) {
//                        NotificationPayload notificationPayload = NotificationPayload.fromOrderAck(orderAckHistory);
//                        return userRepo.findById(orderAckHistory.getUserId())
//                                .flatMap(user -> Mono.fromRunnable(() -> {
//                                            try {
//                                                telegramService.sendNotification(user, notificationPayload);
//                                            } catch (JsonProcessingException e) {
//                                                throw new RuntimeException(e);
//                                            }
//                                        })
//                                        .subscribeOn(Schedulers.boundedElastic())
//                                        .then(Mono.just(orderAckHistory)));
//                    } else {
//                        return Mono.empty(); // Skip processing for null or invalid OrderAckHistory
//                    }
//                })
//                .subscribe();
//    }


    private OrderAckHistory createOrderAck(KlineData klineData, Strategy strategy) {
        OrderAckHistory ack = new OrderAckHistory();
        ack.setPlatform(strategy.getPlatform());
        ack.setSymbol(strategy.getSymbol().getSymbol());
        ack.setEntryPrice(klineData.getCurrentPrice());
        ack.setUsdtAmount(strategy.getAmount());
        ack.setTimestamp(Instant.now().toEpochMilli());
        ack.setUserId(strategy.getUser().getId());
        ack.setOpenPrice(strategy.getCandleWindow().getOpenPrice());
        ack.setStrategy(strategy);
        ack.setCreatedAt(LocalDateTime.now());
        ack.setUpdatedAt(LocalDateTime.now());
        ack.setOrderStatus(OrderStatus.OPEN);
        return ack;
    }

    private static String replaceUsdtSuffix(String input) {
        return input.substring(0, input.length() - 4).concat("_USDT");
    }

    private static String addCandleStickPrefix(String input) {
        return "M".concat(input);
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderPlacerApplication.class, args);
    }

}
