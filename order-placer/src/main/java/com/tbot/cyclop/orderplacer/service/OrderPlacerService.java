package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.NotificationPayload;
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

    @Transactional
    public Order handleSubmitOrder(Strategy strategy, KlineData klineData, Order lastOrder) throws Exception {
        if (lastOrder == null || !OrderStatus.OPEN.equals(lastOrder.getOrderStatus())) {
            Order order = createOrderAck(klineData, strategy);
            double takeProfitPrice = calculateTakeProfitPrice(strategy, klineData);
            order.setCurrentTakeProfitPrice(takeProfitPrice);
            double stopLossPrice = calculateStopLossPrice(strategy, klineData);
            order.setStopLossPrice(stopLossPrice);
            PlatformService service = getService(klineData.getSourcePlatform());
            service.entry(order);
            logger.info("OPENED ORDER {} ON {} SYMBOL {}", order.getPlatformOrderId(), order.getPlatform(), order.getSymbol());
            return order;
        }

        return null;
    }

    @Transactional
    public Order handleSyncStatus(KlineData klineData, Order latestOrder) throws JsonProcessingException {
        if (latestOrder == null) {
            return null;
        } else {
            PlatformService service = getService(klineData.getSourcePlatform());
            service.syncStatus(latestOrder);
            return latestOrder;
        }
    }

    public Order handleReduceTakeProfit(Strategy strategy, KlineData klineData, Order latestOrder) throws JsonProcessingException {
        PlatformService service = getService(klineData.getSourcePlatform());
        service.syncStatus(latestOrder);
        // check to see if order is open on platform
        if (latestOrder == null || !OrderStatus.OPEN.equals(latestOrder.getOrderStatus()) || latestOrder.getPlatformOrderId() == null) {
            return null;
        } else {
            double newTakeProfitPrice = calculateReducedTakeProfitPrice(strategy, klineData, latestOrder);
            latestOrder.setCurrentTakeProfitPrice(newTakeProfitPrice);
            service.reduceProfit(latestOrder, klineData);
            return latestOrder;
        }

    }

    private Order createOrderAck(KlineData klineData, Strategy strategy) {
        Order ack = new Order();
        ack.setPlatform(strategy.getPlatform());
        ack.setSymbol(strategy.getSymbol().getSymbol());
        ack.setEntryPrice(klineData.getCurrentPrice());
        ack.setTimestamp(Instant.now().toEpochMilli());
        ack.setCandleOpenPrice(klineData.getOpenPrice());
        ack.setStrategy(strategy);
        ack.setCreatedAt(LocalDateTime.now());
        ack.setUpdatedAt(LocalDateTime.now());
        ack.setOrderStatus(OrderStatus.SYS_CREATED);
        return ack;
    }

    private PlatformService getService(String platform) {
        return serviceMap.get(platform);
    }
}
