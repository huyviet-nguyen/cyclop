package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.CandleWindowRepo;
import com.tbot.cyclop.orderplacer.repo.StrategyRepo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Set;

import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.*;

@Service
public class OrderPlacerService {

    private final MexcService mexcService;

    private final BybitService bybitService;

    private final CandleWindowRepo candleWindowRepo;

    private final StrategyRepo strategyRepo;

    private final Logger logger = LoggerFactory.getLogger(OrderPlacerService.class);

    private final HashMap<String, PlatformService> serviceMap = new HashMap<>();

    private final Set<OrderStatus> SUBMITABLE_ORDER_STATUS = Set.of(OrderStatus.SYS_CREATED, OrderStatus.MISSED, OrderStatus.STOPPED_LOSS, OrderStatus.TOOK_PROFIT);

    public OrderPlacerService(MexcService mexcService, BybitService bybitService, CandleWindowRepo candleWindowRepo, StrategyRepo strategyRepo) {
        this.mexcService = mexcService;
        this.bybitService = bybitService;
        this.candleWindowRepo = candleWindowRepo;
        this.strategyRepo = strategyRepo;
    }

    @PostConstruct
    void initServiceMap() {
        serviceMap.put("MEXC", mexcService);
        serviceMap.put("BYBIT", bybitService);
    }

    @Transactional
    public void updateCandle(Strategy strategy, KlineData klineData) {
        if (isNewCandle(strategy, klineData)) {
            double lastPump = 0;
            if (strategy.getCandleWindow() == null) {
                CandleWindow newCandle = new CandleWindow();
                newCandle.setPlatform(strategy.getPlatform());
                newCandle.setSymbol(klineData.getSymbol());
                newCandle.setInterval(klineData.getInterval());
                newCandle.setCreatedAt(LocalDateTime.now());
                strategy.setCandleWindow(newCandle);
            } else {
                lastPump = calculateChangePercent(strategy.getCandleWindow().getOpenPrice(), klineData.getOpenPrice());
            }
            strategy.getCandleWindow().setOpenPrice(klineData.getOpenPrice());
            strategy.getCandleWindow().setTimestamp(klineData.getTimestamp());
            strategy.getCandleWindow().setLastPump(lastPump);
            CandleWindow persisted = candleWindowRepo.save(strategy.getCandleWindow()).block();
            strategy.setCandleWindow(persisted);
            strategyRepo.save(strategy).block();
            String message = String.format("CANDLE UPDATE | %s | %s |LAST PUMP %s", klineData.getSymbol(), "M".concat(klineData.getInterval()), lastPump);
            logger.info(message);
        }
    }

    public Order handleCancelOrder(Order order, Strategy strategy) throws JsonProcessingException {
        PlatformService service = getService(strategy.getPlatform());
        service.cancelOrder(order, strategy);
        return order;
    }

    @Transactional
    public Order handleSubmitOrder(Strategy strategy, KlineData klineData, boolean isNewCandle) throws Exception {
        Order order = createOrderAck(klineData, strategy);
        double takeProfitPrice = calculateTakeProfitPrice(strategy, order);
        order.setCurrentTakeProfitPrice(takeProfitPrice);
        double stopLossPrice = calculateStopLossPrice(strategy, order);
        order.setStopLossPrice(stopLossPrice);
        PlatformService service = getService(klineData.getSourcePlatform());
        service.submitOrder(order, strategy);
        logger.info("SUBMIT ORDER {} ON {} SYMBOL {}", order.getPlatformOrderId(), order.getPlatform(), order.getSymbol());
        return order;
    }

    @Transactional
    public Order handleSyncStatus(KlineData klineData, Order latestOrder, Strategy strategy) throws JsonProcessingException {
        PlatformService service = getService(klineData.getSourcePlatform());
        service.syncStatus(latestOrder, strategy);
        return latestOrder;
    }

    public Order handleReduceTakeProfit(Strategy strategy, KlineData klineData, Order latestOrder) throws JsonProcessingException {
        PlatformService service = getService(klineData.getSourcePlatform());
        service.syncStatus(latestOrder, strategy);
        double newTakeProfitPrice = calculateReducedTakeProfitPrice(strategy, klineData, latestOrder);
        latestOrder.setCurrentTakeProfitPrice(newTakeProfitPrice);
        service.reduceProfit(latestOrder, strategy, klineData);
        return latestOrder;
    }

    private Order createOrderAck(KlineData klineData, Strategy strategy) {
        Order ack = new Order();
        ack.setPlatform(strategy.getPlatform());
        ack.setSymbol(strategy.getSymbol().getSymbol());
        ack.setEntryPrice(klineData.getCurrentPrice());
        double openOrderPrice = strategy.getPositionSide().equals("LONG") ? addPercentage(klineData.getCurrentPrice(), strategy.getOrderChange()) : deductPercentage(klineData.getCurrentPrice(), strategy.getOrderChange());
        ack.setOpenOrderPrice(openOrderPrice);
        ack.setTimestamp(Instant.now().toEpochMilli());
        ack.setCandleOpenPrice(klineData.getOpenPrice());
        ack.setCreatedAt(LocalDateTime.now());
        ack.setUpdatedAt(LocalDateTime.now());
        ack.setOrderStatus(OrderStatus.SYS_CREATED);
        return ack;
    }

    private PlatformService getService(String platform) {
        return serviceMap.get(platform);
    }
}
