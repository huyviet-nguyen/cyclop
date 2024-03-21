package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.req.MexcChangePriceRequest;
import com.tbot.cyclop.Cyclop.dto.req.MexcOpenOrderRequest;
import com.tbot.cyclop.Cyclop.dto.res.*;
import com.tbot.cyclop.Cyclop.model.Bot;
import com.tbot.cyclop.Cyclop.model.FingerprintSysInfo;
import com.tbot.cyclop.Cyclop.model.Order;
import com.tbot.cyclop.Cyclop.model.OrderStatus;
import com.tbot.cyclop.orderplacer.exception.OpenOrderFailException;
import com.tbot.cyclop.orderplacer.exception.ReduceTakeProfitFailException;
import com.tbot.cyclop.orderplacer.exception.SyncStatusFailException;
import com.tbot.cyclop.orderplacer.util.TradingUtil;
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
    public void submitOrder(Order newOrder) throws Exception {
        MexcOpenOrderRequest openOrderRequest = orderAckToMexcOpenOrderRequest(newOrder);
        String mHash = openOrderRequest.getMHash();
        String webToken = decryptSecretKey(newOrder.getStrategy().getBot().getWebToken());
        long timestamp = openOrderRequest.getTimestamp();
        String stringPayload = objectMapper.writeValueAsString(openOrderRequest);
        String contentLength = String.valueOf(stringPayload.getBytes(StandardCharsets.UTF_8).length);
        String headerHash = getMexcSign(stringPayload, timestamp, webToken);
        String path = mexcOrderBaseUrl.concat("api/v1/private/order/create?mhash=").concat(mHash);

        MexcOrderResponse response;

        try {
            String responseString = webClient.post()
                    .uri(path)
                    .body(BodyInserters.fromValue(openOrderRequest))
                    .header("Content-Type", "application/json")
                    .header("Content-Length", contentLength)
                    .header("X-Mxc-Nonce", String.valueOf(timestamp))
                    .header("X-Mxc-Sign", headerHash)
                    .header("Authorization", webToken)
                    .retrieve()
                    .bodyToMono(String.class).block();
            response = objectMapper.readValue(responseString, MexcOrderResponse.class);
            if (response == null || response.getData() == null || !response.isSuccess()) {
                throw new OpenOrderFailException(newOrder);
            }
        } catch (Exception e) {
            throw new OpenOrderFailException(newOrder, e);
        }
        newOrder.setPlatformTimestamp(response.getData().getTs());
        newOrder.setPlatformOrderId(response.getData().getOrderId());
        newOrder.setOrderStatus(OrderStatus.SUBMIT);
    }

    @Override
    public void reduceProfit(Order orderWithUpdatedProfit, KlineData marketData) throws JsonProcessingException {
        String webToken = decryptSecretKey(orderWithUpdatedProfit.getStrategy().getBot().getWebToken());
        MexcStopOrderResponse stopOrder = getPendingOrder(orderWithUpdatedProfit.getPlatformOrderId(), webToken);
        MexcChangePriceRequest changePriceRequest = getMexcChangePriceRequest(orderWithUpdatedProfit, stopOrder);
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
            if (response == null || !response.isSuccess()) {
                throw new ReduceTakeProfitFailException(orderWithUpdatedProfit);
            }
        } catch (Exception e) {
            throw new ReduceTakeProfitFailException(orderWithUpdatedProfit, e);
        }
    }

    @NotNull
    private static MexcChangePriceRequest getMexcChangePriceRequest(Order order, MexcStopOrderResponse stopOrder) {
        double pu = order.getStrategy().getSymbol().getPu();
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
    public void syncStatus(Order order) throws JsonProcessingException {
        if (order == null) {
            return;
        }
        if (order.getPlatformOrderId() == null) {
            throw new SyncStatusFailException(order);
        }


        String decryptedWebToken = decryptSecretKey(order.getStrategy().getBot().getWebToken());
        MexcStopOrderResponse response = getPendingOrder(order.getPlatformOrderId(), decryptedWebToken);
        if (response.getState() == 1) {
            order.setOrderStatus(OrderStatus.OPEN);
            return;
        }
        long positionId = Long.parseLong(response.getPositionId());
        MexcOrderHistoryListResponse.MexcOrderHistoryResponse historyResponse = getClosed(positionId, decryptedWebToken);
        if (historyResponse == null) {
            order.setOrderStatus(OrderStatus.MISSED);
            return;
        }
        if (historyResponse.getExternalOid().contains("STOP_LOSS")) {
            order.setOrderStatus(OrderStatus.STOPPED_LOSS);
        } else {
            order.setOrderStatus(OrderStatus.STOPPED_LOSS);
        }
    }


    private MexcOrderHistoryListResponse.MexcOrderHistoryResponse getClosed(String mexcOrderId, String decryptedWebToken) throws JsonProcessingException {
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

        return mexcOrderHistoryListResponse.getData().stream().filter(a -> a.getOrderId().equals(mexcOrderId)).findFirst().orElse(null);
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

    private MexcStopOrderResponse getPendingOrder(String mexcOrderId, String decryptedWebToken) throws JsonProcessingException {
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

//    @PostConstruct
//    public void getPlatformOrder() throws JsonProcessingException {
//        String mexcOrderId = "524706981253043202";
//        String decryptedWebToken = decryptSecretKey("U2FsdGVkX19u01ru8tns+ck3a8jGKiUK5mJa2mrzSnjQvhMUDVx5ZHi+YqWwGa2PXBCligFIvF8nQFwnlA/sVxZC2ovAd958HMFW6pu6cnNw+884OAaI8YbIuazNVuac");
//        long timestamp = System.currentTimeMillis();
//
//        String path = mexcOrderBaseUrl.concat("api/v1/private/order/deal_details/").concat(mexcOrderId);
//        String headerHash = getMexcSign("a", timestamp, decryptedWebToken);
//
//        String response = webClient.get()
//                .uri(path)
//                .header("Content-Type", "application/json")
//                .header("X-Mxc-Nonce", String.valueOf(timestamp))
//                .header("X-Mxc-Sign", headerHash)
//                .header("Authorization", decryptedWebToken)
//                .retrieve()
//                .bodyToMono(String.class).block();
//        System.out.println(response);
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
    public MexcOpenOrderRequest orderAckToMexcOpenOrderRequest(Order sysOrder) throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        double pu = sysOrder.getStrategy().getSymbol().getPu();
        MexcOpenOrderRequest mexcOrder = new MexcOpenOrderRequest();
        String side = sysOrder.getStrategy().getPositionSide().equals("LONG") ? "1" : "3";
        byte[] key = TradingUtil.generateRandomBytes(32);
        mexcOrder.setSide(side);
        mexcOrder.setSymbol(sysOrder.getSymbolWithUnderScore());
        mexcOrder.setLeverage(10);
        mexcOrder.setStopLossPrice(roundToSameDecimal(pu, sysOrder.getStopLossPrice()));
        mexcOrder.setTakeProfitPrice(roundToSameDecimal(pu, sysOrder.getCurrentTakeProfitPrice()));
        mexcOrder.setPrice(roundToSameDecimal(pu, sysOrder.getOpenOrderPrice()));
        mexcOrder.setK0(getMexcK0(bytesToHex(key)));
        FingerprintSysInfo sysInfo = sysOrder.getStrategy().getBot().getFingerprintSysInfo();
        mexcOrder.setP0(getMexcP0(sysInfo, key));
        mexcOrder.setTimestamp(timestamp);
        mexcOrder.setCHash(getMexcCHashs());
        mexcOrder.setMToken(sysInfo.getMtoken());
        mexcOrder.setMHash(sysInfo.getMhash());
        Bot bot = sysOrder.getStrategy().getBot();
        String apiKey = decryptSecretKey(bot.getApiKey());
        String secretKey = decryptSecretKey(bot.getSecretKey());
        double balance = getBalance(apiKey, secretKey);
        double cont = getContractSize(sysOrder.getSymbolWithUnderScore(), sysOrder.getEntryPrice());
        int volume = getVolume(balance, sysOrder.getStrategy().getAmount(), cont);
        mexcOrder.setVol(volume);
        sysOrder.setVolume(volume);
        return mexcOrder;
    }
}
