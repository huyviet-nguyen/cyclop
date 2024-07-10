package com.tbot.cyclop.orderplacer.service;

import com.tbot.cyclop.Cyclop.model.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.tbot.cyclop.orderplacer.util.PercentageUtil.calculateChangePercent;

@Component
public class MarketContextHolder {
    private final ConcurrentMap<String, Double> candlePriceMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Double> candleMaxDiff = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Double> previousCandleMaxDiff = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Double> candlePumpMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Order> orderCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Double> lastOrderCandleOpenPriceMap = new ConcurrentHashMap<>();
    public synchronized void updateCandleMaps(String mapKey, double openPrice, double newPrice) {
        previousCandleMaxDiff.put(mapKey, candleMaxDiff.getOrDefault(mapKey, 0.0));
        double oldOpenPrice = candlePriceMap.getOrDefault(mapKey, 0.0);
        if (openPrice != oldOpenPrice) {
            candlePumpMap.compute(mapKey, (k, oldValue) -> oldValue == null ?
                    calculateChangePercent(0, newPrice) : calculateChangePercent(openPrice, newPrice));
            candlePriceMap.put(mapKey, openPrice);
        }
    }

    public synchronized void updateCandleMaxDiff(String mapKey, double newPrice) {
        double openPrice = candlePriceMap.getOrDefault(mapKey, 0.0);
        double current = candleMaxDiff.getOrDefault(mapKey, 0.0);
        if (Math.abs(newPrice - openPrice) > Math.abs(current)) {
            candleMaxDiff.put(mapKey, Math.abs(newPrice - openPrice));
        }
    }

    public void updateLastOrderCandleOpenPriceMap(String mapKey, double openPrice) {
        lastOrderCandleOpenPriceMap.put(mapKey, openPrice);
    }

    public synchronized double getLastOrderCandleOpenPrice(String mapKey) {
        return lastOrderCandleOpenPriceMap.getOrDefault(mapKey, 0.0);
    }

    public synchronized double getCandleOpenPrice(String mapKey) {
        return candlePriceMap.getOrDefault(mapKey, 0.0);
    }

    public synchronized double getCandleMaxDiff(String mapKey) {
        return candleMaxDiff.getOrDefault(mapKey, 0.0);
    }

    public synchronized double getPreviousCandleMaxDiff(String mapKey) {
        return candleMaxDiff.getOrDefault(mapKey, 0.0);
    }

    public synchronized double getCandlePump(String mapKey) {
        return candlePumpMap.getOrDefault(mapKey, 0.0);
    }

    public synchronized void cacheOrder(String strategyId, Order order) {
        orderCache.put(strategyId, order);
    }

    public synchronized Order getOrder(String strategyId) {
        return orderCache.get(strategyId);
    }
    public synchronized void removeOrder(String strategyId) {
        orderCache.remove(strategyId);
    }
}
