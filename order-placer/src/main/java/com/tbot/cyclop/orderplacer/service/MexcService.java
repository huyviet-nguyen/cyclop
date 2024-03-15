package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.req.MexcOpenOrderRequest;
import com.tbot.cyclop.Cyclop.dto.res.MexcOrderResponse;
import com.tbot.cyclop.Cyclop.model.Bot;
import com.tbot.cyclop.Cyclop.model.FingerprintSysInfo;
import com.tbot.cyclop.Cyclop.model.OrderAckHistory;
import com.tbot.cyclop.orderplacer.exception.OpenOrderFailException;
import com.tbot.cyclop.orderplacer.util.TradingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.calculateHmacSHA256;
import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.decryptSecretKey;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;

@Service
public class MexcService implements PlatformService {

    @Value("${mexc.contract.api.baseUrl}")
    public String mexcContractBaseUrl;

    @Value("${mexc.order.api.baseUrl}")
    public String mexcOrderBaseUrl;
    private final Logger logger = LoggerFactory.getLogger(MexcService.class);

    private final WebClient webClient = WebClient.create();

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public double getUsdtBalance(String decryptedApiKey, String decryptedApiSecret) {
        String path = mexcContractBaseUrl.concat("account/asset/USDT");
        long timestamp = System.currentTimeMillis();
        String objectString = String.join("", decryptedApiKey, String.valueOf(timestamp), "coin=USDT");
        String signature = calculateHmacSHA256(decryptedApiSecret, objectString);
        try {
            return webClient.method(HttpMethod.GET)
                    .uri(path)
                    .header("Content-Type", "application/json")
                    .header("ApiKey", decryptedApiKey)
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

    @Override
    public void entry(OrderAckHistory orderAckHistory) throws Exception {
        MexcOpenOrderRequest openOrderRequest = orderAckToMexcOpenOrderRequest(orderAckHistory);
        String mHash = openOrderRequest.getMHash();
        String apiKey = orderAckHistory.getStrategy().getBot().getApiKey();
        long timestamp = openOrderRequest.getTimestamp();


        Map<String, String> headers = new HashMap<>();

        headers.put("Content-Type", "application/json");
        long contentLength = objectMapper.writeValueAsBytes(openOrderRequest).length;
        headers.put("Content-Length", String.valueOf(contentLength));
        headers.put("X-Mxc-Nonce", String.valueOf(timestamp));
        String headerHash = getSign(openOrderRequest, timestamp, apiKey);
        headers.put("X-Mxc-Sign", headerHash);
        headers.put("Authorization", apiKey);


        String path = mexcOrderBaseUrl.concat("/api/v1/private/order/create?mhash=").concat(mHash);
        MexcOrderResponse response;
        try {
            response = webClient.post()
                    .uri(path)
                    .body(BodyInserters.fromValue(openOrderRequest))
                    .headers(headersHandler -> addHeaders(headersHandler, headers))
                    .retrieve()
                    .bodyToMono(MexcOrderResponse.class).block();
        } catch (Exception e) {
            throw new OpenOrderFailException(orderAckHistory, e);
        }

        if (response == null || response.getResult() == null) {
            throw new OpenOrderFailException(orderAckHistory);
        }

        orderAckHistory.setPlatformOrderStatus(response.getResult().getOrderStatus());
        orderAckHistory.setPlatformOrderId(response.getResult().getOrderId());
        orderAckHistory.setCreatedOnPlatformAt(LocalDateTime.parse(response.getTimeNow()));
    }

    @Override
    public void reduceProfit(OrderAckHistory orderAckHistory) {

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

    public double getContractSize(String symbol, Double price) {
        String apiUrl = mexcContractBaseUrl + "&symbol=" + symbol;
        Mono<String> responseMono = webClient.get()
                .uri(apiUrl)
                .retrieve()
                .bodyToMono(String.class);
        String response = responseMono.block();
        if (response != null) {
            double cont;
            double cs = parseCs(response);
            cont = price * cs;
            return cont;
        } else {
            throw new RuntimeException("Failed to retrieve contract details for symbol: " + symbol);
        }
    }

    private double parseCs(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode csNode = jsonNode.at("/data/0/cs");
            if (csNode.isMissingNode()) {
                throw new RuntimeException("Failed to parse 'cs' from the JSON response.");
            }
            return csNode.asDouble();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse 'cs' from the JSON response.", e);
        }
    }


    private MexcOpenOrderRequest orderAckToMexcOpenOrderRequest(OrderAckHistory ackHistory) throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        MexcOpenOrderRequest mexcOrder = new MexcOpenOrderRequest();
        String side = ackHistory.getStrategy().getPositionSide().equals("LONG") ? "3" : "1";
        byte[] key = TradingUtil.generateRandomBytes(32);
        mexcOrder.setSide(side);
        mexcOrder.setSymbol(ackHistory.getSymbolWithUnderScore());
        mexcOrder.setLeverage(10);
        mexcOrder.setStopLossPrice(ackHistory.getStopLossPrice());
        mexcOrder.setK0(getMexcK0(bytesToHex(key)));
        FingerprintSysInfo sysInfo = ackHistory.getStrategy().getBot().getFingerprintSysInfo();
        mexcOrder.setP0(getMexcP0(sysInfo, key));
        mexcOrder.setTimestamp(timestamp);
        mexcOrder.setCHash(getMexcCHashs());
        mexcOrder.setMToken(sysInfo.getMtoken());
        mexcOrder.setMHash(sysInfo.getMhash());
        Bot bot = ackHistory.getStrategy().getBot();
        String apiKey = decryptSecretKey(bot.getApiKey());
        String secretKey = decryptSecretKey(bot.getSecretKey());
        double balance = getUsdtBalance(apiKey, secretKey);
        double cont = getContractSize(ackHistory.getSymbolWithUnderScore(), ackHistory.getEntryPrice());
        int volume = getVolume(balance, ackHistory.getStrategy().getAmount(), cont);
        mexcOrder.setVol(volume);
        return mexcOrder;
    }

    private static void addHeaders(HttpHeaders httpHeaders, Map<String, String> headersMap) {
        for (Map.Entry<String, String> entry : headersMap.entrySet()) {
            httpHeaders.add(entry.getKey(), entry.getValue());
        }
    }
}
