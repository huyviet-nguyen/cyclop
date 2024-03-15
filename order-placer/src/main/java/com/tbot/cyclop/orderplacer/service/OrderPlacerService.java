package com.tbot.cyclop.orderplacer.service;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.CandleWindowRepo;
import com.tbot.cyclop.orderplacer.repo.OrderAckHistoryRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.decryptSecretKey;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.*;

@Service
public class OrderPlacerService {

    private final OrderAckHistoryRepo historyRepo;

    private final MexcService mexcService;

    private final BybitService bybitService;

    private final CandleWindowRepo candleWindowRepo;

    private final TelegramService telegramService;

    public OrderPlacerService(OrderAckHistoryRepo historyRepo, MexcService mexcService, BybitService bybitService, CandleWindowRepo candleWindowRepo, TelegramService telegramService) {
        this.historyRepo = historyRepo;
        this.mexcService = mexcService;
        this.bybitService = bybitService;
        this.candleWindowRepo = candleWindowRepo;
        this.telegramService = telegramService;
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
        }
    }

    public OrderAckHistory handleIgnore(Strategy strategy, KlineData klineData) {
        return null;
    }

    public OrderAckHistory handleOpenOrder(Strategy strategy, KlineData klineData) {
        OrderAckHistory orderAckHistory = createOrderAck(klineData, strategy);

        return null;
    }

    public OrderAckHistory handleTakeProfit(Strategy strategy, KlineData klineData) {
        return null;
    }

    public OrderAckHistory handleStopLoss(Strategy strategy, KlineData klineData) {
        return null;
    }

    public OrderAckHistory handleReduceTakeProfit(Strategy strategy, KlineData klineData) {
        return null;
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
        ack.setUsdtAmount(strategy.getAmount());
        ack.setTimestamp(Instant.now().toEpochMilli());
        ack.setUserId(strategy.getUser().getId());
        ack.setCandleOpenPrice(strategy.getCandleWindow().getOpenPrice());
        ack.setStrategy(strategy);
        ack.setCreatedAt(LocalDateTime.now());
        ack.setUpdatedAt(LocalDateTime.now());
        ack.setOrderStatus(OrderStatus.OPEN);
        return ack;
    }


}
