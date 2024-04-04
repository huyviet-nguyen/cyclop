package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;

import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.*;

@Service
public class OrderPlacerService {

    private final MexcService mexcService;

    private final BybitService bybitService;

    private final Logger logger = LoggerFactory.getLogger(OrderPlacerService.class);

    private final HashMap<String, PlatformService> serviceMap = new HashMap<>();

    public OrderPlacerService(MexcService mexcService, BybitService bybitService) {
        this.mexcService = mexcService;
        this.bybitService = bybitService;
    }

    @PostConstruct
    void initServiceMap() {
        serviceMap.put("MEXC", mexcService);
        serviceMap.put("BYBIT", bybitService);
    }

    public Order handleCancelOrder(Order order, Strategy strategy) throws JsonProcessingException {
        PlatformService service = getService(strategy.getPlatform());
        service.cancelOrder(order, strategy);
        return order;
    }

    @Transactional
    public Order handleSubmitOrder(Strategy strategy, KlineData klineData) throws Exception {
        Order order = createOrderAck(klineData, strategy);
        double takeProfitPrice = calculateTakeProfitPrice(strategy, order);
        order.setCurrentTakeProfitPrice(takeProfitPrice);
        double stopLossPrice = calculateStopLossPrice(strategy, order);
        order.setStopLossPrice(stopLossPrice);
        PlatformService service = getService(klineData.getSourcePlatform());
        service.submitOrder(order, strategy);
        logger.info("SUBMIT ORDER : {} | {} | {} | {} | {} | PLATFORM ID : {}", order.getSymbol(), order.getOpenOrderPrice(), order.getCurrentTakeProfitPrice(), order.getStopLossPrice(), order.getVolume(), order.getPlatformOrderId());
        return order;
    }

    @Transactional
    public Order handleSyncStatus(KlineData klineData, Order latestOrder, Strategy strategy) throws JsonProcessingException, InterruptedException {
        PlatformService service = getService(klineData.getSourcePlatform());
        service.syncStatus(latestOrder, strategy);
        return latestOrder;
    }

    public Order handleReduceTakeProfit(Strategy strategy, KlineData klineData, Order latestOrder) throws JsonProcessingException, InterruptedException {
        PlatformService service = getService(klineData.getSourcePlatform());
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
        double openOrderPrice = strategy.getPositionSide().equals("LONG") ? addPercentage(klineData.getOpenPrice(), strategy.getOrderChange()) : deductPercentage(klineData.getOpenPrice(), strategy.getOrderChange());
        ack.setOpenOrderPrice(openOrderPrice);
        ack.setTimestamp(System.currentTimeMillis());
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
