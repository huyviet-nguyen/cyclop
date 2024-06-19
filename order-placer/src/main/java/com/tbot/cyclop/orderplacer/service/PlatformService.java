package com.tbot.cyclop.orderplacer.service;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.Order;
import com.tbot.cyclop.Cyclop.model.Strategy;

import java.io.IOException;
import java.net.URISyntaxException;

public interface PlatformService {
    double getBalance(String apiKey, String apiSecret);

    void submitOrder(Order newOrder, Strategy strategy) throws Exception;

    void reduceProfit(Order orderWithUpdatedProfit, Strategy strategy, KlineData marketData) throws IOException, URISyntaxException;

    void syncStatus(Order order, Strategy strategy) throws IOException, InterruptedException, URISyntaxException;

    void cancelOrder(Order order, Strategy strategy) throws IOException, URISyntaxException;
}
