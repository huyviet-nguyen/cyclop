package com.tbot.cyclop.orderplacer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
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
import reactor.core.scheduler.Schedulers;

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
                    long startProcessTime = System.currentTimeMillis();
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

                                OrderAckHistory latestOrder = orderAckHistoryRepo.findFirstByStrategyIdOrderByCreatedAtDesc(strategy.getId()).block();
                                if (newCandle(strategy, value)) {
                                    orderPlacerService.handleCandleWindow(strategy, value);
                                }
                                if (canIgnore(strategy, value)) {
                                    return null;
                                }
                                if (canEntry(strategy, value)) {
                                    try {
                                        return orderPlacerService.handleOpenOrder(strategy, value, latestOrder);
                                    } catch (Exception e) {
                                        logger.error(e.getMessage());
                                    }
                                }
                                if (canTakeProfit(strategy, value) || canStopLoss(strategy, value)) {
                                    try {
                                        return orderPlacerService.handleSyncStatus(value, latestOrder);
                                    } catch (JsonProcessingException e) {
                                        logger.error(e.getMessage());
                                    }
                                }
                                if (mustReduceTakeProfit(strategy, value)) {
                                    try {
                                        return orderPlacerService.handleReduceTakeProfit(strategy, value, latestOrder);
                                    } catch (JsonProcessingException e) {
                                        logger.error(e.getMessage());
                                    }
                                }
                                return null;
                            }
                    );
                    long doneProcessTime = System.currentTimeMillis();
                    if (doneProcessTime - startProcessTime > 100) {
                        logger.warn("LONG PROCESS : {} ms", doneProcessTime - startProcessTime);
                    }
                    return orderAckHistoryRepo.saveAll(orderAckFlux).toIterable();
                }
        );
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderPlacerApplication.class, args);
    }

}
