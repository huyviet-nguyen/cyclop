package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.model.OrderAckHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.calculateHmacSHA256;

@Service
public class BybitService implements PlatformService {

    @Value("${bybit.contract.api.baseUrl}")
    public String bybitBaseUrl;

    private final Logger logger = LoggerFactory.getLogger(BybitService.class);

    @Override
    public double getUsdtBalance(String apiKey, String apiSecret) {
        String path = bybitBaseUrl.concat("wallet/balance?coin=USDT");
        long timestamp = System.currentTimeMillis();
        String objectString = String.join("", String.valueOf(timestamp), apiKey, "5000", "coin=USDT");
        String signature = calculateHmacSHA256(apiSecret, objectString);
        WebClient client = WebClient.create();
        return client.method(HttpMethod.GET)
                .uri(path)
                .header("X-BAPI-SIGN-TYPE", "2")
                .header("Content-Type", "application/json")
                .header("X-BAPI-API-KEY", apiKey)
                .header("X-BAPI-SIGN", signature)
                .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                .header("X-BAPI-RECV-WINDOW", "5000")
                .retrieve()
                .bodyToMono(String.class).doOnError(res -> logger.error(res.getMessage())).map(BybitService::extractAvailableBalanceBybit).block();
    }

    @Override
    public void entry(OrderAckHistory orderAckHistory) {

    }

    @Override
    public void reduceProfit(OrderAckHistory orderAckHistory) {

    }

    @Override
    public void syncPlatformStatus(OrderAckHistory orderAckHistory) {

    }

    private static double extractAvailableBalanceBybit(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonObject = mapper.readTree(jsonResponse);
            return jsonObject.get("result").get("list").get(0).get("walletBalance").asDouble();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
