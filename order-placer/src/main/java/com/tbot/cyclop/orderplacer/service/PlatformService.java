package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.OrderAckHistory;

public interface PlatformService {
    double getBalance(String apiKey, String apiSecret);

    void entry(OrderAckHistory newOrder) throws Exception;

    void reduceProfit(OrderAckHistory orderWithUpdatedProfit, KlineData marketData) throws JsonProcessingException;

    void syncStatus(OrderAckHistory order) throws JsonProcessingException;
}
