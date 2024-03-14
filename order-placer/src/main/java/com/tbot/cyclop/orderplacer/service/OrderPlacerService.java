package com.tbot.cyclop.orderplacer.service;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.OrderAckHistoryRepo;
import org.springframework.stereotype.Service;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.decryptSecretKey;

@Service
public class OrderPlacerService {

    private final OrderAckHistoryRepo historyRepo;

    private final MexcService mexcService;

    private final BybitService bybitService;

    public OrderPlacerService(OrderAckHistoryRepo historyRepo, MexcService mexcService, BybitService bybitService) {
        this.historyRepo = historyRepo;
        this.mexcService = mexcService;
        this.bybitService = bybitService;
    }

    private OrderAckHistory handleIgnore(Strategy strategy, KlineData klineData) {
        return null;
    }

    private OrderAckHistory handleOpenOrder(Strategy strategy, KlineData klineData) {
        return null;
    }

    private OrderAckHistory handleTakeProfit(Strategy strategy, KlineData klineData) {
        return null;
    }

    private OrderAckHistory handleStopLoss(Strategy strategy, KlineData klineData) {
        return null;
    }

    private OrderAckHistory handleReduceTakeProfit(Strategy strategy, KlineData klineData) {
        return null;
    }


    private double getBalance(Strategy strategy) {
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


}
