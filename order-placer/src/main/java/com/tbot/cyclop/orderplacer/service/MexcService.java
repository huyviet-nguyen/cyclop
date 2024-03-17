package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.req.MexcOpenOrderRequest;
import com.tbot.cyclop.Cyclop.dto.res.MexcOrderHistoryResponse;
import com.tbot.cyclop.Cyclop.dto.res.MexcOrderResponse;
import com.tbot.cyclop.Cyclop.model.Bot;
import com.tbot.cyclop.Cyclop.model.FingerprintSysInfo;
import com.tbot.cyclop.Cyclop.model.OrderAckHistory;
import com.tbot.cyclop.Cyclop.model.OrderStatus;
import com.tbot.cyclop.orderplacer.exception.OpenOrderFailException;
import com.tbot.cyclop.orderplacer.exception.SyncStatusFailException;
import com.tbot.cyclop.orderplacer.util.TradingUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

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

    private static final int UNINFORMED_STATE_MEXC = 1;
    private static final int UNCOMPLETED_STATE_MEXC = 2;
    private static final int COMPLETED_STATE_MEXC = 3;
    private static final int CANCELED_STATE_MEXC = 4;
    private static final int INVALID_STATE_MEXC = 4;


    @Override // TESTED
    public double getUsdtBalance(String decryptedApiKey, String decryptedApiSecret) {
        String path = mexcContractBaseUrl.concat("account/asset/USDT");
        long timestamp = System.currentTimeMillis();
        String objectString = String.join("", decryptedApiKey, String.valueOf(timestamp));
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
    @Transactional
    public void entry(OrderAckHistory orderAckHistory) throws Exception {
        MexcOpenOrderRequest openOrderRequest = orderAckToMexcOpenOrderRequest(orderAckHistory);
        String mHash = openOrderRequest.getMHash();
        String webToken = decryptSecretKey(orderAckHistory.getStrategy().getBot().getWebToken());
        long timestamp = openOrderRequest.getTimestamp();

        String contentLength = String.valueOf(objectMapper.writeValueAsBytes(openOrderRequest).length);
        String headerHash = getMexcSign(openOrderRequest, timestamp, webToken);
        String path = mexcOrderBaseUrl.concat("api/v1/private/order/create?mhash=").concat(mHash);

        MexcOrderResponse response;

        try {
            response = webClient.post()
                    .uri(path)
                    .body(BodyInserters.fromValue(openOrderRequest))
                    .header("Content-Type", "application/json")
                    .header("Content-Length", contentLength)
                    .header("X-Mxc-Nonce", String.valueOf(timestamp))
                    .header("X-Mxc-Sign", headerHash)
                    .header("Authorization", webToken)
                    .retrieve()
                    .bodyToMono(MexcOrderResponse.class).block();
            if (response == null || response.getData() == null || !response.isSuccess()) {
                throw new OpenOrderFailException(orderAckHistory);
            }
        } catch (Exception e) {
            throw new OpenOrderFailException(orderAckHistory, e);
        }
        orderAckHistory.setCreatedOnPlatformAt(response.getData().getTs());
        orderAckHistory.setPlatformOrderId(response.getData().getOrderId());
        orderAckHistory.setOrderStatus(OrderStatus.OPEN);
    }

    @Override
    public void reduceProfit(OrderAckHistory orderAckHistory) {

    }

    @Override
    public void syncPlatformStatus(OrderAckHistory orderAckHistory) throws JsonProcessingException {
        if (orderAckHistory == null) {
            return;
        }
        if (orderAckHistory.getPlatformOrderId() == null) {
            throw new SyncStatusFailException(orderAckHistory);
        }


        String decryptedWebToken = decryptSecretKey(orderAckHistory.getStrategy().getBot().getWebToken());
        MexcOrderHistoryResponse historyResponse = getPlatformOrder(orderAckHistory.getPlatformOrderId(), decryptedWebToken);
        double profit = historyResponse.getOrderData().getProfit();
        orderAckHistory.setProfit(profit);
        switch (historyResponse.getOrderData().getState()) {
            case COMPLETED_STATE_MEXC: {
                OrderStatus status = profit > 0 ? OrderStatus.TOOK_PROFIT : OrderStatus.STOPPED_LOSS;
                orderAckHistory.setOrderStatus(status);
            }
            case CANCELED_STATE_MEXC: {
                orderAckHistory.setOrderStatus(OrderStatus.CANCELED);
            }
        }

    }


    private MexcOrderHistoryResponse getPlatformOrder(String mexcOrderId, String decryptedWebToken) throws JsonProcessingException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/order/get/").concat("/").concat(mexcOrderId);
        String headerHash = getMexcSign(null, timestamp, decryptedWebToken);

        return webClient.get()
                .uri(path)
                .header("Content-Type", "application/json")
                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                .header("X-Mxc-Sign", headerHash)
                .header("Authorization", decryptedWebToken)
                .retrieve()
                .bodyToMono(MexcOrderHistoryResponse.class).block();
    }

//    @PostConstruct
//    public void getPlatformOrder() throws JsonProcessingException {
//        String mexcOrderId = "524261631504013824";
//        String decryptedWebToken = decryptSecretKey("U2FsdGVkX1/CgEDh8QFc+pYQjjmPtPFsMVBERy/5Z9rK6ST27amSX0z/YVIbiyzDud7s9N7zVZ/rnB7znToeMhFNAD3E2RQn15T68JztqdvUvL96325GPWM/EFOu8CY8");
//        long timestamp = System.currentTimeMillis();
//
//        String path = mexcOrderBaseUrl.concat("api/v1/private/order/get/").concat("/").concat(mexcOrderId);
//        String headerHash = getMexcSign(null, timestamp, decryptedWebToken);
//
//        webClient.get()
//                .uri(path)
//                .header("Content-Type", "application/json")
//                .header("X-Mxc-Nonce", String.valueOf(timestamp))
//                .header("X-Mxc-Sign", headerHash)
//                .header("Authorization", decryptedWebToken)
//                .retrieve()
//                .bodyToMono(MexcOrderHistoryResponse.class).block();
//    }

    //TESTED
    private static double extractAvailableBalanceMexc(String jsonResponse) {
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

    //TESTED
    public double getContractSize(String symbol, Double price) {
        String apiUrl = mexcOrderBaseUrl.concat("api/v1/contract/detailV2?client=web&symbol=").concat("&symbol=").concat(symbol);
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


    @Transactional
    public MexcOpenOrderRequest orderAckToMexcOpenOrderRequest(OrderAckHistory ackHistory) throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        MexcOpenOrderRequest mexcOrder = new MexcOpenOrderRequest();
        String side = ackHistory.getStrategy().getPositionSide().equals("LONG") ? "3" : "1";
        byte[] key = TradingUtil.generateRandomBytes(32);
        mexcOrder.setSide(side);
        mexcOrder.setSymbol(ackHistory.getSymbolWithUnderScore());
        mexcOrder.setLeverage(10);
        mexcOrder.setStopLossPrice(ackHistory.getStopLossPrice());
        mexcOrder.setTakeProfitPrice(ackHistory.getCurrentTakeProfitPrice());
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
        ackHistory.setVolume(volume);
        return mexcOrder;
    }
}
