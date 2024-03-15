package com.tbot.cyclop.orderplacer.service;

import com.tbot.cyclop.Cyclop.model.OrderAckHistory;

public interface PlatformService {
    double getUsdtBalance(String apiKey, String apiSecret);

    void entry(OrderAckHistory orderAckHistory) throws Exception;

    void reduceProfit(OrderAckHistory orderAckHistory);
}
