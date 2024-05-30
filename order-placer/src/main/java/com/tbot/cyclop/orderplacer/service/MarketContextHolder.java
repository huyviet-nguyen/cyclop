package com.tbot.cyclop.orderplacer.service;

import com.tbot.cyclop.Cyclop.model.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.tbot.cyclop.orderplacer.util.PercentageUtil.calculateChangePercent;

@Component
public class MarketContextHolder {
    private final ConcurrentMap<String, Double> candlePriceMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Double> candlePumpMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Order> orderCache = new ConcurrentHashMap<>();

    public synchronized void updateCandleMaps(String mapKey, double openPrice, double newPrice) {
        double oldOpenPrice = candlePriceMap.getOrDefault(mapKey, 0.0);
        if (openPrice != oldOpenPrice) {
            candlePumpMap.compute(mapKey, (k, oldValue) -> oldValue == null ?
                    calculateChangePercent(0, newPrice) : calculateChangePercent(openPrice, newPrice));
            candlePriceMap.put(mapKey, openPrice);
        }
    }
    public synchronized double getCandleOpenPrice(String mapKey) {
        return candlePriceMap.getOrDefault(mapKey, 0.0);
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
