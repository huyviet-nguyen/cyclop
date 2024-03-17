package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.OrderAckHistory;
import org.springframework.transaction.annotation.Transactional;

public interface PlatformService {
    @Transactional
    double getUsdtBalance(String apiKey, String apiSecret);

    void entry(OrderAckHistory orderAckHistory) throws Exception;

    void reduceProfit(OrderAckHistory orderAckHistory, KlineData klineData) throws JsonProcessingException;

    void syncPlatformStatus(OrderAckHistory orderAckHistory) throws JsonProcessingException;
}
