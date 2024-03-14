package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;


import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.calculateHmacSHA256;

@Service
public class MexcService implements PlatformService {

    @Value("${mexc.api.baseUrl}")
    public String mexcBaseUrl;
    private final Logger logger = LoggerFactory.getLogger(MexcService.class);

    @Override
    public double getUsdtBalance(String apiKey, String apiSecret) {
        String path = mexcBaseUrl.concat("account/asset/USDT");
        long timestamp = System.currentTimeMillis();
        String objectString = String.join("", apiKey, String.valueOf(timestamp), "coin=USDT");
        String signature = calculateHmacSHA256(apiSecret, objectString);
        WebClient client = WebClient.create();
        try {
            return client.method(HttpMethod.GET)
                    .uri(path)
                    .header("Content-Type", "application/json")
                    .header("ApiKey", apiKey)
                    .header("Signature", signature)
                    .header("Request-Time", String.valueOf(timestamp))
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(error -> logger.error(error.getMessage()))
                    .map(MexcService::extractAvailableBalanceMexc)
                    .block();
        } catch (Exception e) {
            logger.info("CANNOT RETRIEVE BALANCE");
            return 0;
        }
    }

    public static double extractAvailableBalanceMexc(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            JsonNode dataNode = rootNode.get("data");
            JsonNode availableBalanceNode = dataNode.get("availableBalance");
            return availableBalanceNode.asDouble();
        } catch (Exception e) {
            return 0;
        }
    }
}
