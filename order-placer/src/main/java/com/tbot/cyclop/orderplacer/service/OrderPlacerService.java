package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.NotificationPayload;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.CandleWindowRepo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.decryptSecretKey;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.*;

@Service
public class OrderPlacerService {

    private final MexcService mexcService;

    private final BybitService bybitService;

    private final CandleWindowRepo candleWindowRepo;

    private final TelegramService telegramService;

    private final Logger logger = LoggerFactory.getLogger(OrderPlacerService.class);

    private final HashMap<String, PlatformService> serviceMap = new HashMap<>();

    public OrderPlacerService(MexcService mexcService, BybitService bybitService, CandleWindowRepo candleWindowRepo, TelegramService telegramService) {
        this.mexcService = mexcService;
        this.bybitService = bybitService;
        this.candleWindowRepo = candleWindowRepo;
        this.telegramService = telegramService;
    }

    @PostConstruct
    void initServiceMap() {
        serviceMap.put("MEXC", mexcService);
        serviceMap.put("BYBIT", bybitService);
    }

    public void handleCandleWindow(Strategy strategy, KlineData klineData) {
        if (newCandle(strategy, klineData)) {
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
            String message = String.format("CANDLE UPDATE | %s | %s |LAST PUMP %s", klineData.getSymbol(), "M".concat(klineData.getInterval()), lastPump);
            logger.info(message);
        }
    }

    public OrderAckHistory handleOpenOrder(Strategy strategy, KlineData klineData, OrderAckHistory lastOrder) throws Exception {
        if (lastOrder == null || OrderStatus.SYS_CREATED.equals(lastOrder.getOrderStatus())) {
            OrderAckHistory orderAckHistory = createOrderAck(klineData, strategy);
            double takeProfitPrice = calculateTakeProfitPrice(strategy, klineData);
            orderAckHistory.setCurrentTakeProfitPrice(takeProfitPrice);
            double stopLossPrice = calculateStopLossPrice(strategy, klineData);
            orderAckHistory.setStopLossPrice(stopLossPrice);
            PlatformService service = getService(klineData.getSourcePlatform());
            service.entry(orderAckHistory);
            sendNotification(orderAckHistory);
            logger.info("OPENED ORDER {} ON {} SYMBOL {}", orderAckHistory.getPlatformOrderId(), orderAckHistory.getPlatform(), orderAckHistory.getSymbol());
            return orderAckHistory;
        }

        return null;
    }

    @Transactional
    public OrderAckHistory handleSyncStatus(KlineData klineData, OrderAckHistory latestOrder) throws JsonProcessingException {
        PlatformService service = getService(klineData.getSourcePlatform());
        service.syncPlatformStatus(latestOrder);
        // check to see if order is open on platform
        if (latestOrder == null || !OrderStatus.OPEN.equals(latestOrder.getOrderStatus()) || latestOrder.getPlatformOrderId() == null) {
            return null;
        } else {
            sendNotification(latestOrder);
            return latestOrder;
        }
    }

    @Transactional
    public OrderAckHistory handleReduceTakeProfit(Strategy strategy, KlineData klineData, OrderAckHistory latestOrder) throws JsonProcessingException {
        PlatformService service = getService(klineData.getSourcePlatform());
        service.syncPlatformStatus(latestOrder);
        // check to see if order is open on platform
        if (latestOrder == null || !OrderStatus.OPEN.equals(latestOrder.getOrderStatus()) || latestOrder.getPlatformOrderId() == null) {
            return null;
        } else {
            double newTakeProfitPrice = calculateReducedTakeProfitPrice(strategy, klineData, latestOrder);
            latestOrder.setCurrentTakeProfitPrice(newTakeProfitPrice);
            service.reduceProfit(latestOrder);
            return latestOrder;
        }

    }


    public double getBalance(Strategy strategy) {
        Bot bot = strategy.getBot();
        if (bot == null) {
            throw new RuntimeException(String.format("NO BOT FOUND FOR STRATEGY %s", strategy.getId()));
        }
        String apiKey = decryptSecretKey(bot.getApiKey());
        String apiSecret = decryptSecretKey(bot.getSecretKey());
        return switch (strategy.getPlatform()) {
            case "BYBIT" -> bybitService.getUsdtBalance(apiKey, apiSecret);
            case "MEXC" -> mexcService.getUsdtBalance(apiKey, apiSecret);
            default -> 0;
        };
    }

    private OrderAckHistory createOrderAck(KlineData klineData, Strategy strategy) {
        OrderAckHistory ack = new OrderAckHistory();
        ack.setPlatform(strategy.getPlatform());
        ack.setSymbol(strategy.getSymbol().getSymbol());
        ack.setEntryPrice(klineData.getCurrentPrice());
        ack.setTimestamp(Instant.now().toEpochMilli());
        ack.setCandleOpenPrice(klineData.getOpenPrice());
        ack.setStrategy(strategy);
        ack.setCreatedAt(LocalDateTime.now());
        ack.setUpdatedAt(LocalDateTime.now());
        ack.setOrderStatus(OrderStatus.SYS_CREATED);
        ack.setLastTakeProfitPercent(calculateNewValue(strategy.getOrderChange(), strategy.getTakeProfit()));
        return ack;
    }

    private PlatformService getService(String platform) {
        return serviceMap.get(platform);
    }

    public void sendNotification(OrderAckHistory orderAckHistory) {
        try {
            NotificationPayload notificationPayload = NotificationPayload.fromOrderAck(orderAckHistory);
            telegramService.sendNotification(orderAckHistory.getStrategy(), notificationPayload);
        } catch (Exception e) {
            logger.error("CANNOT SEND NOTIFICATION");
        }
    }


}
