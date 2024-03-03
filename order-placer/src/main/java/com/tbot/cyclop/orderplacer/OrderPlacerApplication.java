package com.tbot.cyclop.orderplacer;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.*;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Function;

@SpringBootApplication
@EnableReactiveMongoRepositories
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

    Logger logger = LoggerFactory.getLogger(OrderPlacerApplication.class);

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, OrderAckHistory>> process() {
        return data -> data.flatMapValues((key, value) -> {
            String candleStick = addCandleStickPrefix(value.getInterval());
            String symbolString = replaceUsdtSuffix(value.getSymbol());
            String positionSide = value.getCurrentPrice() > value.getOpenPrice() ? "LONG" : "SHORT";
            Symbol symbol = symbolRepo.findBySymbolAndPlatform(symbolString, value.getSourcePlatform()).block();
            if (symbol == null) {
                return new ArrayList<>();
            }
            Flux<Strategy> strategyFlux = strategyRepo.findByCandleStickAndSymbol(candleStick, symbol).filter(strategy -> strategy.getPositionSide().equals(positionSide) || strategy.getPositionSide().equals("BOTH"));
            Flux<OrderAckHistory> orderAckFlux = strategyFlux.publishOn(Schedulers.boundedElastic()).mapNotNull(strategy -> {
                boolean newCandle = renewCandleWindow(strategy, value);
                double strategyEntryPnlPercentage = (strategy.getOrderChange() * strategy.getExtendOrderChangePercent()) / 100;
                double currentPnlPercentage = (value.getCurrentPrice() - value.getOpenPrice()) / value.getOpenPrice() * 100;
                boolean pumping = currentPnlPercentage > 0;
                if (strategy.getStrategyMarker() == null) {
                    StrategyMarker marker = new StrategyMarker();
                    marker.setActualTp(strategy.getTakeProfit());
                    strategy.setStrategyMarker(marker);
                }

                double actualTakeProfit = strategy.getStrategyMarker().getActualTp();

                OrderAckHistory orderAckHistory = orderAckHistoryRepo.findFirstByStrategyIdOrderByTimestampDesc(strategy.getId()).block();
                Bot bot = strategy.getBot();

                if (orderAckHistory == null) {
                    if (currentPnlPercentage > strategyEntryPnlPercentage) {
                        return new OrderAckHistory(value.getSourcePlatform()
                                , bot.getApiKey()
                                , value.getCurrentPrice(), strategy.getId(), Instant.now().toString(), strategy.getAmount(), OrderAction.ENTRY, value.getSymbol(), strategy.getUser().getId());
                    }
                } else {
                    if (!orderAckHistory.getOrderAction().equals(OrderAction.ENTRY)) {
                        if (currentPnlPercentage > strategyEntryPnlPercentage) {
                            return new OrderAckHistory(value.getSourcePlatform()
                                    , bot.getApiKey()
                                    , value.getCurrentPrice(), strategy.getId(), Instant.now().toString(), strategy.getAmount(), OrderAction.ENTRY, value.getSymbol(), strategy.getUser().getId());
                        }
                    } else {
                        if (pumping) {
                            if (currentPnlPercentage > actualTakeProfit && !newCandle) {
                                return new OrderAckHistory(value.getSourcePlatform()
                                        , bot.getApiKey()
                                        , value.getCurrentPrice(), strategy.getId(), Instant.now().toString(), strategy.getAmount(), OrderAction.TAKE_PROFIT, value.getSymbol(), strategy.getUser().getId());
                            } else {
                                StrategyMarker marker = strategy.getStrategyMarker();
                                marker.setActualTp(reduceByPercentage(marker.getActualTp(), strategy.getReduceTakeProfit()));
                            }
                        }

                        if (!pumping && currentPnlPercentage > strategy.getStopLoss()) {
                            return new OrderAckHistory(value.getSourcePlatform()
                                    , bot.getApiKey()
                                    , value.getCurrentPrice(), strategy.getId(), Instant.now().toString(), strategy.getAmount(), OrderAction.STOP_LOSS, value.getSymbol(), strategy.getUser().getId());
                        }
                    }
                }
                strategyRepo.save(strategy).subscribe();
                return null;
            });
            String message = String.format("Processed message : %s from platform %s", value.getTimestamp(), value.getSourcePlatform());
            logger.info(message);
            return orderAckHistoryRepo.saveAll(orderAckFlux.toIterable()).toIterable();
        });
    }

    private static double reduceByPercentage(double original, double reducePercent) {
        double reductionAmount = (original * reducePercent) / 100;
        return original - reductionAmount;
    }

    private boolean renewCandleWindow(Strategy strategy, KlineData klineData) {
        CandleWindow candleWindow = new CandleWindow();
        if (strategy.getCandleWindow() == null || klineData.getOpenPrice() != strategy.getCandleWindow().getOpenPrice()) {
            candleWindow.setPlatform(strategy.getPlatform());
            candleWindow.setOpenPrice(klineData.getOpenPrice());
            candleWindow.setSymbol(replaceUsdtSuffix(klineData.getSymbol()));
            strategy.setCandleWindow(candleWindow);
            return true;
        }
        return false;
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
