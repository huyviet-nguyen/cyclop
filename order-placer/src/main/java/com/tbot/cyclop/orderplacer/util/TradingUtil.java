package com.tbot.cyclop.orderplacer.util;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;

import static com.tbot.cyclop.orderplacer.util.PercentageUtil.*;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.calculateNewValue;

public class TradingUtil {

    private static final double ONE_HUNDRED_PERCENT = 100;

    public static boolean canIgnore(KlineData klineData, Strategy strategy) {
        double lastPump = strategy.getCandleWindow().getLastPump();
        double ignorePercent = calculateNewValue(lastPump, strategy.getIgnore());
        double changePercent = calculateChangePercent(klineData.getOpenPrice(), klineData.getCurrentPrice());
        return Math.abs(changePercent) < Math.abs(ignorePercent);
    }

    public static boolean canEntry(KlineData klineData, Strategy strategy) {
        double changePercent = calculateChangePercent(klineData.getOpenPrice(), klineData.getCurrentPrice());
        double expectedSide = switch (strategy.getPositionSide()) {
            case "LONG" -> 1;
            case "SHORT" -> -1;
            default -> 0; // for BOTH
        };
        double expectedChangePercent = calculateNewValue(strategy.getOrderChange(), strategy.getExtendOrderChangePercent());
        if (expectedSide == 0) {
            return Math.abs(changePercent) > expectedChangePercent;
        }
        return changePercent > expectedChangePercent * expectedSide;
    }

    public static boolean entryAlready(OrderAckHistory history) {
        return history == null || !OrderStatus.OPEN.equals(history.getOrderStatus());
    }

    public static double calculateTakeProfitPercent(Strategy strategy) {
        return calculateNewValue(strategy.getOrderChange(), strategy.getTakeProfit());
    }

    public static double calculateTakeProfitPrice(Strategy strategy, KlineData klineData) {
        double takeProfitPercent = calculateTakeProfitPercent(strategy);
        return calculateNewValue(ONE_HUNDRED_PERCENT + takeProfitPercent, klineData.getOpenPrice());
    }

    public static double calculateStopLossPercent(Strategy strategy) {
        return calculateNewValue(strategy.getOrderChange(), strategy.getStopLoss());
    }

    public static double calculateStopLossPrice(Strategy strategy, KlineData klineData) {
        double stopLossPercent = calculateStopLossPercent(strategy);
        return calculateNewValue(ONE_HUNDRED_PERCENT - stopLossPercent, klineData.getOpenPrice());
    }

    public static boolean canTakeProfit(Strategy strategy, KlineData klineData) {
        return klineData.getCurrentPrice() > calculateTakeProfitPrice(strategy, klineData);
    }

    public static boolean canStopLoss(Strategy strategy, KlineData klineData) {
        return klineData.getCurrentPrice() < calculateStopLossPrice(strategy, klineData);
    }

    public static boolean newCandle(Strategy strategy, KlineData klineData) {
        CandleWindow candleWindow = strategy.getCandleWindow();
        if (candleWindow == null) return true;
        double lastOpenPrice = candleWindow.getOpenPrice();
        return lastOpenPrice != klineData.getOpenPrice();
    }

    public static boolean mustReduceTakeProfit(Strategy strategy, KlineData klineData) {
        return newCandle(strategy, klineData) && !canTakeProfit(strategy, klineData);
    }

    public static double calculateReducedTakeProfitPrice(Strategy strategy, KlineData klineData) {
        StrategyMarker marker = strategy.getStrategyMarker();
        double lastTakeProfitPercent;
        if (marker != null) {
            lastTakeProfitPercent = marker.getActualTp();
        } else {
            lastTakeProfitPercent = strategy.getTakeProfit();
        }
        double newTakeProfitPercent = deductPercentage(lastTakeProfitPercent, strategy.getReduceTakeProfit());
        return calculateNewValue(klineData.getOpenPrice(), ONE_HUNDRED_PERCENT + newTakeProfitPercent);
    }
}
