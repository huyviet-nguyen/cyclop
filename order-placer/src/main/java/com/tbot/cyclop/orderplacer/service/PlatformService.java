package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.Order;

public interface PlatformService {
    double getBalance(String apiKey, String apiSecret);

    void submitOrder(Order newOrder) throws Exception;

    void reduceProfit(Order orderWithUpdatedProfit, KlineData marketData) throws JsonProcessingException;

    void syncStatus(Order order) throws JsonProcessingException;
}
