package com.tbot.cyclop.orderplacer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.NotificationPayload;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.*;
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
import java.util.ArrayList;
import java.util.function.Function;

import static com.tbot.cyclop.orderplacer.service.GenericHttpUtil.decryptSecretKey;

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
    private final Logger logger = LoggerFactory.getLogger(OrderPlacerApplication.class);

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, OrderAckHistory>> process() {
        return stringKlineDataKStream -> stringKlineDataKStream.flatMapValues(
                (key, value) ->
                {
                    String candleStick = addCandleStickPrefix(value.getInterval());
                    String symbolString = replaceUsdtSuffix(value.getSymbol());
                    String positionSide = value.getCurrentPrice() > value.getOpenPrice() ? "LONG" : "SHORT";
                    Symbol symbol = symbolRepo.findBySymbolAndPlatform(symbolString, value.getSourcePlatform()).cache().block();
                    if (symbol == null) {
                        return new ArrayList<>();
                    }

                    Flux<Strategy> strategyFlux = strategyRepo.findByCandleStickAndSymbol(candleStick, symbol).filter(strategy -> (strategy.getPositionSide().equals(positionSide) || strategy.getPositionSide().equals("BOTH")) && "ACTIVE".equals(strategy.getStatus()));
                    Flux<OrderAckHistory> orderAckFlux = strategyFlux.publishOn(Schedulers.boundedElastic()).mapNotNull(
                            (Strategy strategy) ->
                            {
                                boolean newCanle = renewCandleWindow(strategy, value);
                                if (canIgnore(value, strategy)) {
                                    return null;
                                }
                                OrderAckHistory orderAckHistory = orderAckHistoryRepo.findFirstByStrategyIdOrderByTimestampDesc(strategy.getId()).block();
                                if (orderAckHistory == null) {
                                    return handleNewOrder(value, strategy);
                                } else {
                                    if (!OrderAction.ENTRY.equals(orderAckHistory.getOrderAction())) {
                                        return handleNewOrder(value, strategy);
                                    } else {
                                        if (canStopLoss(value, strategy)) {
                                            return handleStopLoss(value, strategy);
                                        }

                                        if (canTakeProfit(value, strategy)) {
                                            return handleTakeProfit(value, strategy);
                                        } else {
                                            if (newCanle) {
                                                handleReduceTakeProfit(strategy);
                                            }
                                            return null;
                                        }
                                    }
                                }
                            }
                    );
                    notify(orderAckFlux);
                    return orderAckHistoryRepo.saveAll(orderAckFlux).toIterable();
                }
        );
    }

    private void notify(Flux<OrderAckHistory> orderAckHistoryFlux) {
        orderAckHistoryFlux.publishOn(Schedulers.boundedElastic()).doOnEach(ack -> {
            OrderAckHistory orderAckHistory = ack.get();
            if (orderAckHistory != null && orderAckHistory.getStrategy() != null) {
                NotificationPayload notificationPayload = NotificationPayload.fromOrderAck(orderAckHistory);
                Mono<User> telegramIdMoni = userRepo.findById(orderAckHistory.getUserId());
                telegramIdMoni.doOnSuccess(user -> {
                    try {
                        telegramService.sendNotification(user, notificationPayload);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }).block();
            }
        }).subscribe();
    }

    private boolean canIgnore(KlineData klineData, Strategy strategy) {
        double currentChangePercent = (klineData.getCurrentPrice() - strategy.getCandleWindow().getOpenPrice()) / strategy.getCandleWindow().getOpenPrice() * 100;
        boolean canIgnore = Math.abs(currentChangePercent) < Math.abs(strategy.getIgnore() * strategy.getCandleWindow().getLastPump() / 100);
        if (canIgnore) {
            String message = String.format("IGNORE CHANGE ON %s | EXPECTED : %s | CURR : %s", klineData.getSymbol(), (Math.abs(strategy.getIgnore() * strategy.getCandleWindow().getLastPump() / 100)), currentChangePercent);
            logger.info(message);
        }
        return canIgnore;
    }

    private boolean canTakeProfit(KlineData klineData, Strategy strategy) {
        if (strategy.getStrategyMarker() != null) {
            double currentChangePercent = (klineData.getCurrentPrice() - strategy.getCandleWindow().getOpenPrice()) / strategy.getCandleWindow().getOpenPrice() * 100;
            double actualTakeProfitPercent = strategy.getStrategyMarker().getActualTp() * strategy.getOrderChange() / 100 ;
            return currentChangePercent > actualTakeProfitPercent;
        } else {
            double currentChangePercent = (klineData.getCurrentPrice() - strategy.getCandleWindow().getOpenPrice()) / strategy.getCandleWindow().getOpenPrice() * 100;
            double actualTakeProfitPercent = strategy.getTakeProfit() * strategy.getOrderChange() / 100;
            return currentChangePercent > actualTakeProfitPercent;
        }

    }

    private boolean canStopLoss(KlineData klineData, Strategy strategy) {
        double currentChangePercent = (klineData.getCurrentPrice() - strategy.getCandleWindow().getOpenPrice()) / strategy.getCandleWindow().getOpenPrice() * 100;
        return currentChangePercent > strategy.getStopLoss() * strategy.getOrderChange() / 100;
    }

    private OrderAckHistory handleStopLoss(KlineData klineData, Strategy strategy) {
        OrderAckHistory ack = createOrderAck(klineData, strategy);
        ack.setOrderAction(OrderAction.STOP_LOSS);
        String logMessage = String.format("STOPPED LOSS | %s | OPEN: %s | CURR: %s | USR : %s", klineData.getSymbol(), klineData.getOpenPrice(), klineData.getCurrentPrice(), strategy.getUser().getName());
        logger.info(logMessage);
        return ack;
    }

    private OrderAckHistory handleTakeProfit(KlineData klineData, Strategy strategy) {
        OrderAckHistory ack = createOrderAck(klineData, strategy);
        if (strategy.getStrategyMarker() == null) {
            strategy.setStrategyMarker(new StrategyMarker());
        }
        strategy.getStrategyMarker().setActualTp(strategy.getTakeProfit());
        strategyMarkerRepo.save(strategy.getStrategyMarker()).block();
        ack.setOrderAction(OrderAction.TAKE_PROFIT);
        String logMessage = String.format("TOOK PROFIT | %s | OPEN: %s | CURR: %s | USR : %s", klineData.getSymbol(), klineData.getOpenPrice(), klineData.getCurrentPrice(), strategy.getUser().getName());
        logger.info(logMessage);
        return ack;
    }

    private OrderAckHistory createOrderAck(KlineData klineData, Strategy strategy) {
        OrderAckHistory ack = new OrderAckHistory();
        ack.setPlatform(strategy.getPlatform());
        ack.setSymbol(strategy.getSymbol().getSymbol());
        ack.setPrice(klineData.getCurrentPrice());
        ack.setApiKey(strategy.getBot().getApiKey());
        ack.setAmount(strategy.getAmount());
        ack.setTimestamp(Instant.now().toEpochMilli());
        ack.setUserId(strategy.getUser().getId());
        ack.setStrategyId(strategy.getId());
        ack.setOpenPrice(strategy.getCandleWindow().getOpenPrice());
        ack.setStrategy(strategy);
        ack.setApiSecret(strategy.getBot().getSecretKey());
        return ack;
    }

    private void handleReduceTakeProfit(Strategy strategy) {
        if (strategy.getStrategyMarker() == null) {
            StrategyMarker marker = new StrategyMarker();
            marker.setActualTp(strategy.getTakeProfit() - strategy.getTakeProfit() * strategy.getReduceTakeProfit() / 100);
            strategy.setStrategyMarker(strategyMarkerRepo.save(marker).block());
        } else {
            double lastTp = strategy.getStrategyMarker().getActualTp();
            strategy.getStrategyMarker().setActualTp(strategy.getStrategyMarker().getActualTp() - strategy.getStrategyMarker().getActualTp() * strategy.getReduceTakeProfit() / 100);
            String message = String.format("REDUCED TAKE PROFIT | STRATEGY: %s | LAST TP: %s | CURRENT TP: %s", String.join("#", strategy.getUser().getName(), strategy.getId()), lastTp, strategy.getStrategyMarker().getActualTp());
            logger.info(message);
        }
        strategyRepo.save(strategy).block();
    }

    private OrderAckHistory handleNewOrder(KlineData klineData, Strategy strategy) {
        double currentChangePercent = (klineData.getCurrentPrice() - strategy.getCandleWindow().getOpenPrice()) / strategy.getCandleWindow().getOpenPrice() * 100;
        double entryPercent = strategy.getOrderChange() * strategy.getExtendOrderChangePercent() / 100;
        if ((strategy.getPositionSide().equals("LONG") && currentChangePercent < 0) || (strategy.getPositionSide().equals("SHORT") && currentChangePercent > 0)) {
            return null;
        }
        if (Math.abs(currentChangePercent) > entryPercent) {
            OrderAckHistory ack = createOrderAck(klineData, strategy);
            ack.setOrderAction(OrderAction.ENTRY);
            String logMessage = String.format("ENTRY PLACED | %s | OPEN: %s | CURR: %s | USR : %s", klineData.getSymbol(), klineData.getOpenPrice(), klineData.getCurrentPrice(), strategy.getUser().getName());
            logger.info(logMessage);
            return ack;
        }
        return null;
    }

    private boolean renewCandleWindow(Strategy strategy, KlineData klineData) {
        if (strategy.getCandleWindow() != null) {
            double lastPrice = strategy.getCandleWindow().getOpenPrice();
            boolean newCandle = klineData.getOpenPrice() != strategy.getCandleWindow().getOpenPrice();
            if (newCandle) {
                double lastPump = (klineData.getOpenPrice() - strategy.getCandleWindow().getOpenPrice()) / strategy.getCandleWindow().getOpenPrice() * 100;
                strategy.getCandleWindow().setLastPump(lastPump);
                strategy.getCandleWindow().setOpenPrice(klineData.getOpenPrice());
                candleWindowRepo.save(strategy.getCandleWindow()).block();
                String logMessage = String.format("CANDLE UPDATED | %s | LAST PRICE: %s | CURR: %s | PUMP : %s| INTERVAL : %s", klineData.getSymbol(), lastPrice, klineData.getOpenPrice(), lastPump, klineData.getInterval());
                logger.info(logMessage);
            }
            strategyRepo.save(strategy).block();
            return newCandle;
        } else {
            CandleWindow candleWindow = new CandleWindow();
            candleWindow.setOpenPrice(klineData.getOpenPrice());
            candleWindow.setPlatform(strategy.getPlatform());
            candleWindow.setSymbol(replaceUsdtSuffix(klineData.getSymbol()));
            candleWindow.setInterval(klineData.getInterval());
            candleWindow.setTimestamp(klineData.getTimestamp());
            strategy.setCandleWindow(candleWindowRepo.save(candleWindow).block());
            strategyRepo.save(strategy).block();
            return true;
        }
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
