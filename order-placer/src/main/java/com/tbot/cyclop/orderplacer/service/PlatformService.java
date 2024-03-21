package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.Order;
import com.tbot.cyclop.Cyclop.model.Strategy;

public interface PlatformService {
    double getBalance(String apiKey, String apiSecret);

    void submitOrder(Order newOrder, Strategy strategy) throws Exception;

    void reduceProfit(Order orderWithUpdatedProfit, Strategy strategy, KlineData marketData) throws JsonProcessingException;

    void syncStatus(Order order, Strategy strategy) throws JsonProcessingException;

    void cancelOrder(Order order, Strategy strategy) throws JsonProcessingException;
}
