package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.req.MexcChangePriceRequest;
import com.tbot.cyclop.Cyclop.dto.req.MexcOpenOrderRequestV1;
import com.tbot.cyclop.Cyclop.dto.res.*;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.exception.OpenOrderFailException;
import com.tbot.cyclop.orderplacer.exception.ReduceTakeProfitFailException;
import com.tbot.cyclop.orderplacer.repo.HttpRequestLogRepo;
import com.tbot.cyclop.orderplacer.repo.OrderRepo;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import static com.tbot.cyclop.Cyclop.HttpConstant.*;
import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.calculateHmacSHA256;
import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.decryptSecretKey;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.normalizeDouble;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.roundToSameDecimal;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@Service
public class MexcService implements PlatformService {

    @Value("${mexc.contract.api.baseUrl}")
    public String mexcContractBaseUrl;

    @Value("${mexc.order.api.baseUrl}")
    public String mexcOrderBaseUrl;
    private final Logger logger = LoggerFactory.getLogger(MexcService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int LEVERAGE = 10;

    private final OrderRepo orderRepo;

    private final HttpRequestLogRepo requestLogRepo;


    public MexcService(OrderRepo orderRepo, HttpRequestLogRepo requestLogRepo) {
        this.orderRepo = orderRepo;
        this.requestLogRepo = requestLogRepo;
    }


    @Override // TESTED
    public double getBalance(String decryptedApiKey, String decryptedApiSecret) {
        String path = mexcContractBaseUrl.concat("account/asset/USDT");
        long timestamp = System.currentTimeMillis();
        String objectString = String.join("", decryptedApiKey, String.valueOf(timestamp));
        String signature = calculateHmacSHA256(decryptedApiSecret, objectString);
        try {
            String responseString = WebClient.create().method(HttpMethod.GET).uri(path).header(CONTENT_TYPE_HEADER_NAME, APPLICATION_JSON).header("ApiKey", decryptedApiKey).header("Signature", signature).header("Request-Time", String.valueOf(timestamp)).retrieve().bodyToMono(String.class).block();
            return responseString == null ? 0 : extractAvailableBalanceMexc(responseString);
        } catch (Exception e) {
            logger.info("CANNOT RETRIEVE BALANCE");
            return 0;
        }
    }

    private void appendLog(String url, String reqBody, String resBody, LocalDateTime reqTime, LocalDateTime resTime, String symbol, String strategyId) {
        HttpRequestLog log = new HttpRequestLog();
        log.setUrl(url);
        log.setRequestTime(reqTime);
        log.setResponseTime(resTime);
        log.setRequestBody(reqBody);
        log.setResponseBody(resBody);
        log.setSymbolString(symbol);
        log.setStrategyId(strategyId);
        requestLogRepo.save(log).block();
    }

    @Override
    @Transactional
    public void submitOrder(Order newOrder, Strategy strategy) throws Exception {
        MexcOpenOrderRequestV1 openOrderRequest = orderAckToMexcOpenOrderRequestV1(newOrder, strategy);
        String mHash = openOrderRequest.getMHash();
        String webToken = decryptSecretKey(strategy.getBot().getWebToken());
        long timestamp = openOrderRequest.getTimestamp();
        String stringPayload = objectMapper.writeValueAsString(openOrderRequest);
        String headerHash = getMexcSign(stringPayload, timestamp, webToken);
        String path = mexcOrderBaseUrl.concat("api/v1/private/order/create?mhash=").concat(mHash);


        if (openOrderRequest.getVol() > 0) {
            try {
                String responseString = reqRestTemplate(HttpMethod.POST, path, objectMapper.writeValueAsString(openOrderRequest), strategy.getSymbolString(), webToken, headerHash, timestamp, 10, strategy.getId());
                MexcOrderResponse response = objectMapper.readValue(responseString, MexcOrderResponse.class);
                if (response == null || response.getData() == null || !response.isSuccess()) {
                    throw new OpenOrderFailException("FAILED TO CREATE ORDER");
                }
                MexcOrderResponse.MexcOrderResponseInner orderResponseInner = response.getData();
                newOrder.setPlatformOrderId(String.valueOf(orderResponseInner.getOrderId()));
                logger.info("SUBMIT ORDER {} ON {} SYMBOL {}", newOrder.getPlatformOrderId(), newOrder.getPlatform(), newOrder.getSymbol());
                newOrder.setPlatformTimestamp(System.currentTimeMillis());
                newOrder.setOrderStatus(OrderStatus.SUBMIT);
            } catch (Exception e) {
                throw new OpenOrderFailException(newOrder, e);
            }
        } else {
            logger.error("CANNOT PLACE ORDER FOR {}, YOU'RE BROKE!", newOrder.getSymbol());
            throw new OpenOrderFailException("BALANCE NOT ENOUGH");
        }
    }

    @Override
    public void reduceProfit(Order orderWithUpdatedProfit, Strategy strategy, KlineData marketData) throws IOException, URISyntaxException {
        String webToken = decryptSecretKey(strategy.getBot().getWebToken());
        MexcStopOrderListResponse stopOrderList = getStopOrderOpenOrders(webToken, strategy.getId());
        MexcStopOrderResponse stopOrder = stopOrderList.getData().stream().filter(a -> Objects.equals(a.getOrderId(), orderWithUpdatedProfit.getPlatformOrderId())).findFirst().orElse(null);
        if (stopOrder == null) {
            logger.error("CANNOT FIND OPENED ORDER {} ON PLATFORM, CANNOT REDUCE TAKE-PROFIT", orderWithUpdatedProfit.getPlatformOrderId());
            String foundId = objectMapper.writeValueAsString(stopOrderList);
            throw new ReduceTakeProfitFailException(orderWithUpdatedProfit, foundId);
        }
        MexcChangePriceRequest changePriceRequest = getMexcChangePriceRequest(orderWithUpdatedProfit, stopOrder, strategy);
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/stoporder/change_plan_order");
        String stringPayload = objectMapper.writeValueAsString(changePriceRequest);
        String headerHash = getMexcSign(stringPayload, timestamp, webToken);
        MexcChangeOrderResponse response;
        String stringResponse = "";
        try {
            stringResponse = reqRestTemplate(HttpMethod.POST, path, stringPayload, strategy.getSymbolString(), webToken, headerHash, timestamp, 10, strategy.getId());
        } catch (Exception e){
            orderWithUpdatedProfit.setErrorMessage(e.getLocalizedMessage());
            orderRepo.save(orderWithUpdatedProfit).block();
            logger.error("CANNOT CANCEL ORDER : {}", orderWithUpdatedProfit.getPlatformOrderId());
            throw e;
        }
        response = objectMapper.readValue(stringResponse, MexcChangeOrderResponse.class);
        logger.info("REDUCED TAKE PROFIT FOR ORDER {}", orderWithUpdatedProfit.getPlatformOrderId());
        if (response == null || !response.isSuccess()) {
            throw new ReduceTakeProfitFailException(orderWithUpdatedProfit);
        }
    }

    @NotNull
    private static MexcChangePriceRequest getMexcChangePriceRequest(Order order, MexcStopOrderResponse stopOrder, Strategy strategy) {
        double pu = strategy.getSymbol().getPu();
        MexcChangePriceRequest changePriceRequest = new MexcChangePriceRequest();
        changePriceRequest.setTakeProfitPrice(normalizeDouble(roundToSameDecimal(pu, order.getCurrentTakeProfitPrice())));
        changePriceRequest.setStopLossPrice(normalizeDouble(roundToSameDecimal(pu, order.getStopLossPrice())));
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
    public void syncStatus(Order order, Strategy strategy) throws IOException, URISyntaxException {
        String decryptedWebToken = decryptSecretKey(strategy.getBot().getWebToken());
        MexcOrderHistoryListResponse listResponse;
        Optional<MexcStopOrderResponse> closedFound;

        if (OrderStatus.SUBMIT.equals(order.getOrderStatus())) {
            listResponse = getOrderListHistoryOrder(decryptedWebToken, strategy.getSymbolString(), strategy.getId());
            Optional<MexcOrderHistoryListResponse.MexcOrderHistoryResponse> openedFound = listResponse.getData().stream().filter(res -> order.getPlatformOrderId().equals(res.getOrderId())).findFirst();
            if (openedFound.isPresent()) {
                MexcOrderHistoryListResponse.MexcOrderHistoryResponse opened = openedFound.get();
                switch (opened.getState()) {
                    case 2:
                        break;
                    case 3:
                        order.setOrderStatus(OrderStatus.OPEN);
                        order.setPlatformBuyPrice(opened.getPrice());
                        order.setRealAmount(opened.getPrice() * opened.getVol() * opened.getLeverage() * strategy.getSymbol().getCs());
                        break;
                    default:
                        order.setOrderStatus(OrderStatus.IGNORED);
                        break;
                }
                return;
            }
        }

        if (OrderStatus.OPEN.equals(order.getOrderStatus())) {
            closedFound = getStopOrderListOrder(decryptedWebToken, strategy.getSymbolString(), strategy.getId()).getData().stream().filter(res -> res.getOrderId().equals(order.getPlatformOrderId())).findFirst();
            if (closedFound.isPresent()) {
                order.setOrderStatus(OrderStatus.CLOSED);
                MexcOrderHistoryListResponse historyResponse = getOrderListHistoryOrder(decryptedWebToken, strategy.getSymbolString(), strategy.getId());
                MexcOrderHistoryListResponse.MexcOrderHistoryResponse closedHistory = historyResponse.getData().stream().filter(res -> res.getOrderId().equals(closedFound.get().getPlaceOrderId())).findFirst().orElse(null);
                if (closedHistory != null) {
                    order.setOrderStatus(OrderStatus.CLOSED);
                    order.setProfit(closedHistory.getProfit());
                    order.setPlatformSellPrice(closedHistory.getDealAvgPrice());
                    return;
                } else {
                    order.setOrderStatus(OrderStatus.IGNORED);
                }
            }
        }

        logger.info("ORDER {} STATUS : {}", order.getPlatform(), order.getOrderStatus());
    }

    @Override
    public void cancelOrder(Order order, Strategy strategy) throws IOException, URISyntaxException {
        long timestamp = System.currentTimeMillis();
        String decryptWebToken = decryptSecretKey(strategy.getBot().getWebToken());
        String path = mexcOrderBaseUrl.concat("api/v1/private/order/cancel");
        String payload = order.toCancelPayload();
        String headerHash = getMexcSign(payload, timestamp, decryptWebToken);

        try {
            String responseString = reqRestTemplate(HttpMethod.POST, path, payload, strategy.getSymbolString(), decryptWebToken, headerHash, timestamp, 10, strategy.getId());
            logger.info("CANCEL ORDER : {}", responseString);
        } catch (Exception e) {
            order.setErrorMessage(e.getLocalizedMessage());
            orderRepo.save(order).block();
            logger.error("CANNOT CANCEL ORDER : {}", order.getPlatformOrderId());
            throw e;
        }
    }

    private MexcOrderHistoryListResponse getOrderListHistoryOrder(String decryptedWebToken, String symbol, String strategyId) throws IOException, URISyntaxException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/order/list/history_orders?category=1,6&page_num=1&page_size=50&symbol=").concat(symbol);
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);
        String responseString = reqRestTemplate(HttpMethod.GET, path, null, symbol, decryptedWebToken, headerHash, timestamp, 20, strategyId);
        return objectMapper.readValue(responseString, MexcOrderHistoryListResponse.class);
    }

    @Deprecated
    public String reqWithRetry(HttpMethod method, String path, String payload, String symbol, String decryptedWebToken, String headerHash, long timestamp, int timeout, String strategyId) {
        int attempt = 0;
        int maxRetry = 5;
        int retryInterval = 3;
        while (attempt < maxRetry) {
            try {
                return reqRestTemplate(method, path, payload, symbol, decryptedWebToken, headerHash, timestamp, timeout, strategyId);
            } catch (RuntimeException | IOException | URISyntaxException e) {
                attempt++;
                if (attempt >= maxRetry) {
                    throw new RuntimeException("Max retry attempts reached", e);
                }
                try {
                    Thread.sleep(retryInterval * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Failed to complete the request after " + maxRetry + " attempts");
    }


    private String reqRestTemplate(HttpMethod method, String path, String payload, String symbol, String decryptedWebToken, String headerHash, long timestamp, int timeout, String strategyId) throws IOException, URISyntaxException {
        LocalDateTime reqTime = LocalDateTime.now();
        String responseString = "";

        CloseableHttpClient httpClient = HttpClientSingleton.getHttpClient();

        URI uri = new URI(path);
        HttpRequestBase httpRequest;
        if (method.equals(HttpMethod.GET)) {
            httpRequest = new HttpGet(uri);
        } else {
            httpRequest = new HttpPost(uri);
            ((HttpPost) httpRequest).setEntity(new StringEntity(payload, StandardCharsets.UTF_8));
        }

        httpRequest.setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        httpRequest.setHeader("X-MXC-NONCE", String.valueOf(timestamp));
        httpRequest.setHeader("X-MXC-SIGN", headerHash);
        httpRequest.setHeader("Authorization", decryptedWebToken);
        httpRequest.setHeader("dnt", "1");
        httpRequest.setHeader("language", "English");
        httpRequest.setHeader("origin", "https://futures.mexc.com");
        httpRequest.setHeader("pragma", "akamai-x-cache-on");
        httpRequest.setHeader("priority", "u=1, i");
        httpRequest.setHeader("referer", "https://futures.mexc.com/vi-VN/exchange/LPT_USDT?type=linear_swap");
        httpRequest.setHeader("sec-ch-ua", "\"Not-A.Brand\";v=\"99\", \"Chromium\";v=\"124\"");
        httpRequest.setHeader("sec-ch-ua-mobile", "?0");
        httpRequest.setHeader("sec-ch-ua-platform", "\"Windows\"");
        httpRequest.setHeader("sec-fetch-dest", "empty");
        httpRequest.setHeader("sec-fetch-mode", "cors");
        httpRequest.setHeader("sec-fetch-site", "same-origin");
        httpRequest.setHeader("trochilus-trace-id", "76d182ac-097c-477e-9b1e-fdb5bdba57c5-0226");
        httpRequest.setHeader("trochilus-uid", "20738208");
        httpRequest.setHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        CloseableHttpResponse response = httpClient.execute(httpRequest);
        int statusCode = response.getStatusLine().getStatusCode();
        responseString = EntityUtils.toString(response.getEntity());

        LocalDateTime respTime = LocalDateTime.now();
        appendLog(path, payload, responseString, reqTime, respTime, symbol, strategyId);

        if (statusCode >= 400 && statusCode < 500) {
            throw new RuntimeException(responseString);
        }

        return responseString;
    }


    private MexcStopOrderListResponse getStopOrderOpenOrders(String decryptedWebToken, String strategyId) throws IOException, URISyntaxException {
        long timestamp = System.currentTimeMillis();
        String path = mexcOrderBaseUrl.concat("api/v1/private/stoporder/open_orders?page_num=1&page_size=100");
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);
        String responseString = reqRestTemplate(GET, path, null, "", decryptedWebToken, headerHash, timestamp, 20, strategyId);
        return objectMapper.readValue(responseString, MexcStopOrderListResponse.class);
    }

    private MexcStopOrderListResponse getStopOrderListOrder(String decryptedWebToken, String symbol, String strategyId) throws IOException, URISyntaxException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/stoporder/list/orders?is_finished=1&page_num=1&page_size=20&symbol=").concat(symbol);
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);
        String responseString = reqRestTemplate(GET, path, null, symbol, decryptedWebToken, headerHash, timestamp, 20, strategyId);
        return objectMapper.readValue(responseString, MexcStopOrderListResponse.class);
    }

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

//    @Deprecated
//    public MexcOpenOrderRequestV2 orderAckToMexcOpenOrderRequestV2(Order sysOrder, Strategy strategy) throws Exception {
//        long timestamp = Instant.now().toEpochMilli();
//        double pu = strategy.getSymbol().getPu();
//        MexcOpenOrderRequestV2 mexcOrder = new MexcOpenOrderRequestV2();
//        String side = strategy.getPositionSide().equals("LONG") ? "1" : "3";
//        String triggerType = strategy.getPositionSide().equals("LONG") ? "2" : "1";
//        byte[] key = generateRandomBytes(32);
//        mexcOrder.setSide(Integer.parseInt(side));
//        mexcOrder.setTriggerType(Integer.parseInt(triggerType));
//        mexcOrder.setSymbol(sysOrder.getSymbol());
//        mexcOrder.setLeverage(LEVERAGE);
//        mexcOrder.setStopLossPrice(String.valueOf(roundToSameDecimal(pu, sysOrder.getStopLossPrice())));
//        mexcOrder.setTakeProfitPrice(String.valueOf(roundToSameDecimal(pu, sysOrder.getCurrentTakeProfitPrice())));
//        mexcOrder.setTriggerPrice(String.valueOf(roundToSameDecimal(pu, sysOrder.getOpenOrderPrice())));
//        mexcOrder.setK0(getMexcK0(bytesToHex(key)));
//        FingerprintSysInfo sysInfo = strategy.getBot().getFingerprintSysInfo();
//        mexcOrder.setP0(getMexcP0(sysInfo, key));
//        mexcOrder.setTimestamp(timestamp);
//        mexcOrder.setCHash(getMexcCHashs());
//        mexcOrder.setMToken(sysInfo.getMtoken());
//        mexcOrder.setMHash(sysInfo.getMhash());
//        Bot bot = strategy.getBot();
//        String apiKey = decryptSecretKey(bot.getApiKey());
//        String secretKey = decryptSecretKey(bot.getSecretKey());
//        double balance = getBalance(apiKey, secretKey);
//        double cont = strategy.getSymbol().getCs() * sysOrder.getOpenOrderPrice();
//        int volume = getVolume(balance, strategy.getRealAmount(), cont);
//        logger.info("ACCOUNT {} | BALANCE : {} | VOL {}", strategy.getBot().getName(), balance, volume);
//        mexcOrder.setVol(volume);
//        sysOrder.setVolume(volume);
//        sysOrder.setOpenOrderPrice(Double.parseDouble(mexcOrder.getTriggerPrice()));
//        sysOrder.setCurrentTakeProfitPrice(Double.parseDouble(mexcOrder.getTakeProfitPrice()));
//        sysOrder.setStopLossPrice(Double.parseDouble(mexcOrder.getStopLossPrice()));
//        return mexcOrder;
//    }

    public MexcOpenOrderRequestV1 orderAckToMexcOpenOrderRequestV1(Order sysOrder, Strategy strategy) throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        double pu = strategy.getSymbol().getPu();
        MexcOpenOrderRequestV1 mexcOrder = new MexcOpenOrderRequestV1();
        String side = strategy.getPositionSide().equals("LONG") ? "1" : "3";
        byte[] key = generateRandomBytes(32);
        mexcOrder.setSide(Integer.parseInt(side));
        mexcOrder.setSymbol(sysOrder.getSymbol());
        mexcOrder.setLeverage(LEVERAGE);
        mexcOrder.setStopLossPrice(normalizeDouble(roundToSameDecimal(pu, sysOrder.getStopLossPrice())));
        mexcOrder.setTakeProfitPrice(normalizeDouble(roundToSameDecimal(pu, sysOrder.getCurrentTakeProfitPrice())));
        mexcOrder.setPrice(normalizeDouble(roundToSameDecimal(pu, sysOrder.getOpenOrderPrice())));
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
        double cont = strategy.getSymbol().getCs() * sysOrder.getOpenOrderPrice();
        int volume = getVolume(balance, strategy.getRealAmount(), cont);
        logger.info("ACCOUNT {} | BALANCE : {} | VOL {}", strategy.getBot().getName(), balance, volume);
        mexcOrder.setVol(volume);
        sysOrder.setVolume(volume);
        sysOrder.setOpenOrderPrice(Double.parseDouble(mexcOrder.getPrice()));
        sysOrder.setCurrentTakeProfitPrice(Double.parseDouble(mexcOrder.getTakeProfitPrice()));
        sysOrder.setStopLossPrice(Double.parseDouble(mexcOrder.getStopLossPrice()));
        return mexcOrder;
    }

}
