package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.req.MexcChangePriceRequest;
import com.tbot.cyclop.Cyclop.dto.req.MexcOpenOrderRequest;
import com.tbot.cyclop.Cyclop.dto.res.*;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.exception.OpenOrderFailException;
import com.tbot.cyclop.orderplacer.exception.ReduceTakeProfitFailException;
import com.tbot.cyclop.orderplacer.util.TradingUtil;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.calculateHmacSHA256;
import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.decryptSecretKey;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.roundToSameDecimal;
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

    private static final int LEVERAGE = 2;

    @Override // TESTED
    public double getBalance(String decryptedApiKey, String decryptedApiSecret) {
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
    public void submitOrder(Order newOrder, Strategy strategy) throws Exception {
        MexcOpenOrderRequest openOrderRequest = orderAckToMexcOpenOrderRequest(newOrder, strategy);
        String mHash = openOrderRequest.getMHash();
        String webToken = decryptSecretKey(strategy.getBot().getWebToken());
        long timestamp = openOrderRequest.getTimestamp();
        String stringPayload = objectMapper.writeValueAsString(openOrderRequest);
        String contentLength = String.valueOf(stringPayload.getBytes(StandardCharsets.UTF_8).length);
        String headerHash = getMexcSign(stringPayload, timestamp, webToken);
        String path = mexcOrderBaseUrl.concat("api/v1/private/planorder/place/v2?mhash=").concat(mHash);


        if (openOrderRequest.getVol() > 0) {
            try {
                MexcOrderResponse response = webClient.post()
                        .uri(path)
                        .body(BodyInserters.fromValue(openOrderRequest))
                        .header("Content-Type", "application/json")
                        .header("Content-Length", contentLength)
                        .header("X-Mxc-Nonce", String.valueOf(timestamp))
                        .header("X-Mxc-Sign", headerHash)
                        .header("Authorization", webToken)
                        .retrieve()
                        .bodyToMono(MexcOrderResponse.class).block();
                if (response == null || response.getData() == 0 || !response.isSuccess()) {
                    throw new OpenOrderFailException(response != null ? response.getMessage() : "");
                }
                newOrder.setPlatformOrderId(String.valueOf(response.getData()));
                logger.info("SUBMIT ORDER {} ON {} SYMBOL {}", newOrder.getPlatformOrderId(), newOrder.getPlatform(), newOrder.getSymbol());
                newOrder.setPlatformTimestamp(System.currentTimeMillis());
                newOrder.setOrderStatus(OrderStatus.SUBMIT);
            } catch (Exception e) {
                throw new OpenOrderFailException(newOrder, e);
            }
        } else {
            logger.error("CANNOT PLACE ORDER FOR {}, YOU'RE BROKE!", newOrder.getSymbol());
        }
    }

    @Override
    public void reduceProfit(Order orderWithUpdatedProfit, Strategy strategy, KlineData marketData) throws JsonProcessingException {
        String webToken = decryptSecretKey(strategy.getBot().getWebToken());
        MexcStopOrderResponse stopOrder = getOpenedOrder(orderWithUpdatedProfit.getPlatformOrderId(), webToken);
        MexcChangePriceRequest changePriceRequest = getMexcChangePriceRequest(orderWithUpdatedProfit, stopOrder, strategy);
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/stoporder/change_plan_order");
        String stringPayload = objectMapper.writeValueAsString(changePriceRequest);
        String contentLength = String.valueOf(stringPayload.getBytes(StandardCharsets.UTF_8).length);
        String headerHash = getMexcSign(stringPayload, timestamp, webToken);
        MexcChangeOrderResponse response;
        try {
            String stringResponse = webClient.post()
                    .uri(path)
                    .body(BodyInserters.fromValue(changePriceRequest))
                    .header("Content-Type", "application/json")
                    .header("Content-Length", contentLength)
                    .header("X-Mxc-Nonce", String.valueOf(timestamp))
                    .header("X-Mxc-Sign", headerHash)
                    .header("Authorization", webToken)
                    .retrieve()
                    .bodyToMono(String.class).block();
            response = objectMapper.readValue(stringResponse, MexcChangeOrderResponse.class);
            logger.info("REDUCED TAKE PROFIT FOR ORDER {}", orderWithUpdatedProfit.getPlatformOrderId());
            if (response == null || !response.isSuccess()) {
                throw new ReduceTakeProfitFailException(orderWithUpdatedProfit);
            }
        } catch (Exception e) {
            throw new ReduceTakeProfitFailException(orderWithUpdatedProfit, e);
        }
    }

    @NotNull
    private static MexcChangePriceRequest getMexcChangePriceRequest(Order order, MexcStopOrderResponse stopOrder, Strategy strategy) {
        double pu = strategy.getSymbol().getPu();
        MexcChangePriceRequest changePriceRequest = new MexcChangePriceRequest();
        changePriceRequest.setTakeProfitPrice(roundToSameDecimal(pu, order.getCurrentTakeProfitPrice()));
        changePriceRequest.setStopLossPrice(roundToSameDecimal(pu, order.getStopLossPrice()));
        changePriceRequest.setOrderId(stopOrder.getId());
        changePriceRequest.setProfitTrend(stopOrder.getProfitTrend());
        changePriceRequest.setLossTrend(stopOrder.getLossTrend());
        changePriceRequest.setStopLossReverse(stopOrder.getStopLossReverse());
        changePriceRequest.setTakeProfitReverse(stopOrder.getTakeProfitReverse());
        changePriceRequest.setTakeProfitVolume(stopOrder.getTakeProfitVol());
        changePriceRequest.setStopLossVolume(stopOrder.getStopLossVol());
        return changePriceRequest;
    }

    @Override
    public void syncStatus(Order order, Strategy strategy) throws JsonProcessingException {
        String decryptedWebToken = decryptSecretKey(strategy.getBot().getWebToken());
        MexcStopOrderResponse openedOrder = getStopOrderBySymbolTpSlVol(order.getSymbol(), order.getCurrentTakeProfitPrice(), order.getStopLossPrice(), order.getVolume(), decryptedWebToken);
        MexcOrderHistoryListResponse.MexcOrderHistoryResponse historyResponse = getHistoryOrder(order.getPositionId(), decryptedWebToken);
        if (openedOrder != null) {
            order.setPositionId(Long.parseLong(openedOrder.getPositionId()));
            order.setOrderStatus(OrderStatus.OPEN);
            if (historyResponse != null && historyResponse.getPositionId() != 0) {
                if (historyResponse.getProfit() > 0) {
                    order.setOrderStatus(OrderStatus.TOOK_PROFIT);
                } else if (historyResponse.getProfit() < 0) {
                    order.setOrderStatus(OrderStatus.STOPPED_LOSS);
                } else {
                    order.setOrderStatus(OrderStatus.CLOSED_UNKNOWN);
                }
            }
        }
        logger.info("ORDER {} STATUS : {}", order.getPlatform(), order.getOrderStatus());
    }

    @Override
    public void cancelOrder(Order order, Strategy strategy) throws JsonProcessingException {
        long timestamp = System.currentTimeMillis();
        String decryptWebToken = decryptSecretKey(strategy.getBot().getWebToken());
        String path = mexcOrderBaseUrl.concat("api/v1/private/planorder/cancel");
        String payload = order.toCancelPayload();
        String contentLength = String.valueOf(payload.getBytes(StandardCharsets.UTF_8).length);
        String headerHash = getMexcSign(payload, timestamp, decryptWebToken);

        try {
            String responseString = webClient.post()
                    .uri(path)
                    .header("Content-Type", "application/json")
                    .header("X-Mxc-Nonce", String.valueOf(timestamp))
                    .header("X-Mxc-Sign", headerHash)
                    .header("Content-Length", contentLength)
                    .header("Authorization", decryptWebToken)
                    .body(BodyInserters.fromValue(payload))
                    .retrieve()
                    .bodyToMono(String.class).block();
            logger.info("CANCEL ORDER : {}", responseString);
            order.setOrderStatus(OrderStatus.CANCELED);
        } catch (Exception e) {
            logger.error("CANNOT CANCEL ORDER : {}", order.getPlatformOrderId());
        }
    }


    private MexcOrderHistoryListResponse.MexcOrderHistoryResponse getHistoryOrder(long positionId, String decryptedWebToken) throws JsonProcessingException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/order/list/history_orders");
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);

        try {
            MexcOrderHistoryListResponse mexcOrderHistoryListResponse = webClient.get()
                    .uri(path)
                    .header("Content-Type", "application/json")
                    .header("X-Mxc-Nonce", String.valueOf(timestamp))
                    .header("X-Mxc-Sign", headerHash)
                    .header("Authorization", decryptedWebToken)
                    .retrieve()
                    .bodyToMono(MexcOrderHistoryListResponse.class).block();

            return mexcOrderHistoryListResponse.getData().stream().filter(a -> a.getPositionId() == positionId).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private MexcOrderHistoryListResponse.MexcOrderHistoryResponse getClosed(long positionId, String decryptedWebToken) throws JsonProcessingException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/order/list/history_orders");
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);

        MexcOrderHistoryListResponse mexcOrderHistoryListResponse = webClient.get()
                .uri(path)
                .header("Content-Type", "application/json")
                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                .header("X-Mxc-Sign", headerHash)
                .header("Authorization", decryptedWebToken)
                .retrieve()
                .bodyToMono(MexcOrderHistoryListResponse.class).block();

        return mexcOrderHistoryListResponse.getData().stream().filter(a -> a.getPositionId() == positionId).findFirst().orElse(null);
    }

    private MexcStopOrderResponse getOpenedOrder(String mexcOrderId, String decryptedWebToken) throws JsonProcessingException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/stoporder/open_orders");
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);

        MexcStopOrderListResponse mexcOrderHistoryListResponse = webClient.get()
                .uri(path)
                .header("Content-Type", "application/json")
                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                .header("X-Mxc-Sign", headerHash)
                .header("Authorization", decryptedWebToken)
                .retrieve()
                .bodyToMono(MexcStopOrderListResponse.class).block();
        assert mexcOrderHistoryListResponse != null;
        return mexcOrderHistoryListResponse.getData().stream().filter(a -> a.getOrderId().equals(mexcOrderId)).findFirst().orElse(null);
    }

    private MexcStopOrderResponse getStopOrderBySymbolTpSlVol(String symbol, double takeProfit, double stopLoss, int vol, String decryptedWebToken) throws JsonProcessingException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/stoporder/list/orders");
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);

        MexcStopOrderListResponse mexcOrderHistoryListResponse = webClient.get()
                .uri(path)
                .header("Content-Type", "application/json")
                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                .header("X-Mxc-Sign", headerHash)
                .header("Authorization", decryptedWebToken)
                .retrieve()
                .bodyToMono(MexcStopOrderListResponse.class).block();
        assert mexcOrderHistoryListResponse != null;
        return mexcOrderHistoryListResponse.getData().stream().filter(a -> {
            return a.getSymbol().equals(symbol) && a.getVol() == vol && a.getTakeProfitPrice() == takeProfit && a.getStopLossPrice() == stopLoss;
        }).findFirst().orElse(null);
    }

//    @PostConstruct
//    public void getPlatformOrder() throws JsonProcessingException {
//        String mexcOrderId = "525654021252650050";
//        String decryptedWebToken = "WEB7a25d6f5cce3f05d5474397dadbbb2e094fbc3b57337d43acfbb5517180e2803";
//        long timestamp = System.currentTimeMillis();
//
//        String openUrl = mexcOrderBaseUrl.concat("api/v1/private/stoporder/list/orders");
//        String historyUrl = mexcOrderBaseUrl.concat("api/v1/private/order/list/history_orders");
//        String holdingPosition = mexcOrderBaseUrl.concat("api/v1/private/position/open_positions");
//        String openOrderNotStopOrder = mexcOrderBaseUrl.concat("api/v1/private/order/list/open_orders");
//        String headerHash = getMexcSign("a", timestamp, decryptedWebToken);
//        String response0 = webClient.get()
//                .uri(openOrderNotStopOrder)
//                .header("Content-Type", "application/json")
//                .header("X-Mxc-Nonce", String.valueOf(timestamp))
//                .header("X-Mxc-Sign", headerHash)
//                .header("Authorization", decryptedWebToken)
//                .retrieve()
//                .bodyToMono(String.class).block();
//        System.out.println(response0);
//
//        String response = webClient.get()
//                .uri(openUrl)
//                .header("Content-Type", "application/json")
//                .header("X-Mxc-Nonce", String.valueOf(timestamp))
//                .header("X-Mxc-Sign", headerHash)
//                .header("Authorization", decryptedWebToken)
//                .retrieve()
//                .bodyToMono(String.class).block();
//        System.out.println(response);
//
//        String response2 = webClient.get()
//                .uri(historyUrl)
//                .header("Content-Type", "application/json")
//                .header("X-Mxc-Nonce", String.valueOf(timestamp))
//                .header("X-Mxc-Sign", headerHash)
//                .header("Authorization", decryptedWebToken)
//                .retrieve()
//                .bodyToMono(String.class).block();
//        System.out.println(response2);
//        String response3 = webClient.get()
//                .uri(holdingPosition)
//                .header("Content-Type", "application/json")
//                .header("X-Mxc-Nonce", String.valueOf(timestamp))
//                .header("X-Mxc-Sign", headerHash)
//                .header("Authorization", decryptedWebToken)
//                .retrieve()
//                .bodyToMono(String.class).block();
//        System.out.println(response3);
//
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
    public MexcOpenOrderRequest orderAckToMexcOpenOrderRequest(Order sysOrder, Strategy strategy) throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        double pu = strategy.getSymbol().getPu();
        MexcOpenOrderRequest mexcOrder = new MexcOpenOrderRequest();
        String side = strategy.getPositionSide().equals("LONG") ? "1" : "3";
        String triggerType = strategy.getPositionSide().equals("LONG") ? "1" : "2";
        byte[] key = TradingUtil.generateRandomBytes(32);
        mexcOrder.setSide(Integer.parseInt(side));
        mexcOrder.setTriggerType(Integer.parseInt(triggerType));
        mexcOrder.setSymbol(sysOrder.getSymbol());
        mexcOrder.setLeverage(LEVERAGE);
        mexcOrder.setStopLossPrice(String.valueOf(roundToSameDecimal(pu, sysOrder.getStopLossPrice())));
        mexcOrder.setTakeProfitPrice(String.valueOf(roundToSameDecimal(pu, sysOrder.getCurrentTakeProfitPrice())));
        mexcOrder.setTriggerPrice(String.valueOf(roundToSameDecimal(pu, sysOrder.getOpenOrderPrice())));
        mexcOrder.setK0(getMexcK0(bytesToHex(key)));
        FingerprintSysInfo sysInfo = strategy.getBot().getFingerprintSysInfo();
        mexcOrder.setP0(getMexcP0(sysInfo, key));
        mexcOrder.setTimestamp(timestamp);
        mexcOrder.setCHash(getMexcCHashs());
        mexcOrder.setMToken(sysInfo.getMtoken());
        mexcOrder.setMHash(sysInfo.getMhash());
        Bot bot = strategy.getBot();
        String apiKey = decryptSecretKey(bot.getApiKey());
        String secretKey = decryptSecretKey(bot.getSecretKey());
        double balance = getBalance(apiKey, secretKey);
        double cont = getContractSize(sysOrder.getSymbol(), sysOrder.getEntryPrice());
        int volume = getVolume(balance, strategy.getAmount(), cont);
        mexcOrder.setVol(volume);
        sysOrder.setVolume(volume);
        //update price to match decimal
        sysOrder.setOpenOrderPrice(Double.parseDouble(mexcOrder.getTriggerPrice()));
        sysOrder.setCurrentTakeProfitPrice(Double.parseDouble(mexcOrder.getTakeProfitPrice()));
        sysOrder.setStopLossPrice(Double.parseDouble(mexcOrder.getStopLossPrice()));
        return mexcOrder;
    }
}
