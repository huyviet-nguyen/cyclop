package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.req.bybit.BybitCancelOrderReq;
import com.tbot.cyclop.Cyclop.dto.req.bybit.BybitOrderReq;
import com.tbot.cyclop.Cyclop.dto.req.bybit.BybitOrderStatus;
import com.tbot.cyclop.Cyclop.dto.req.bybit.BybitReduceTpReq;
import com.tbot.cyclop.Cyclop.dto.res.bybit.*;
import com.tbot.cyclop.Cyclop.model.HttpRequestLog;
import com.tbot.cyclop.Cyclop.model.Order;
import com.tbot.cyclop.Cyclop.model.OrderStatus;
import com.tbot.cyclop.Cyclop.model.Strategy;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.tbot.cyclop.Cyclop.HttpConstant.APPLICATION_JSON;
import static com.tbot.cyclop.Cyclop.HttpConstant.CONTENT_TYPE_HEADER_NAME;
import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.calculateHmacSHA256;
import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.decryptSecretKey;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.roundToSameDecimal;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.getBybitQuantity;

@Service
public class BybitService implements PlatformService {

    @Value("${bybit.contract.api.baseUrl}")
    public String bybitBaseUrl;

    private static final double LEVERAGE = 9.9;

    private final Logger logger = LoggerFactory.getLogger(BybitService.class);

    private final ObjectMapper objectMapper;

    private final HttpRequestLogRepo requestLogRepo;

    private final OrderRepo orderRepo;

    private final MarketContextHolder marketContextHolder;


    public BybitService(HttpRequestLogRepo requestLogRepo, OrderRepo orderRepo, MarketContextHolder marketContextHolder) {
        this.requestLogRepo = requestLogRepo;
        this.orderRepo = orderRepo;
        this.marketContextHolder = marketContextHolder;
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public double getBalance(String apiKey, String apiSecret) {
        String path = bybitBaseUrl.concat("account/wallet/balance?coin=USDT");
        long timestamp = System.currentTimeMillis();
        String objectString = String.join("", String.valueOf(timestamp), apiKey, "15000", "coin=USDT");
        String signature = calculateHmacSHA256(apiSecret, objectString);
        WebClient client = WebClient.create();
        return client.method(HttpMethod.GET)
                .uri(path)
                .header("X-BAPI-SIGN-TYPE", "2")
                .header(CONTENT_TYPE_HEADER_NAME, APPLICATION_JSON)
                .header("X-BAPI-API-KEY", apiKey)
                .header("X-BAPI-SIGN", signature)
                .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                .header("X-BAPI-RECV-WINDOW", "15000")
                .retrieve()
                .bodyToMono(String.class).doOnError(res -> logger.error(res.getMessage())).map(BybitService::extractAvailableBalanceBybit).block();
    }


    @Override
    public void submitOrder(Order newOrder, Strategy strategy) {
        String apiKey = decryptSecretKey(strategy.getBot().getApiKey());
        String secretKey = decryptSecretKey(strategy.getBot().getSecretKey());
        double balance = getBalance(apiKey, secretKey);

        BybitOrderReq bybitOrderReq = mapToBybitOrder(newOrder, strategy, balance);
        if (Double.parseDouble(bybitOrderReq.getQuantity()) == 0) {
            throw new OpenOrderFailException("BALANCE NOT ENOUGH");
        }
        try {
            String response = reqRestTemplate(HttpMethod.POST, bybitBaseUrl.concat("order/create"), objectMapper.writeValueAsString(bybitOrderReq), strategy);
            BybitOrderRes res = objectMapper.readValue(response, BybitOrderRes.class);
            if (res.getRetCode() != 0) {
                logger.error("Error submitting order: {}", res.getRetMsg());
                throw new OpenOrderFailException(res.getRetMsg());
            }
            newOrder.setPlatformOrderId(String.valueOf(res.getResult().getOrderId()));
            logger.info("SUBMIT ORDER {} ON {} SYMBOL {}", newOrder.getPlatformOrderId(), newOrder.getPlatform(), newOrder.getSymbol());
            newOrder.setPlatformTimestamp(res.getTime());
            newOrder.setOrderStatus(OrderStatus.SUBMIT);
        } catch (IOException | URISyntaxException e) {
            throw new OpenOrderFailException(newOrder, e);
        }
    }

    private BybitOrderReq mapToBybitOrder(Order order, Strategy strategy, double balance) {
        BybitOrderReq bybitOrderReq = new BybitOrderReq();
        if (strategy.getPositionSide().equals("LONG")) {
            bybitOrderReq.setSide("Buy");
        } else {
            bybitOrderReq.setSide("Sell");
        }
        bybitOrderReq.setSymbol(order.getSymbol());
        bybitOrderReq.setPrice(String.valueOf(roundToSameDecimal(strategy.getSymbol().getPu(), order.getOpenOrderPrice())));
        bybitOrderReq.setQuantity(String.valueOf(roundToSameDecimal(strategy.getSymbol().getPu(), getBybitQuantity(balance, strategy.getRealAmount(), LEVERAGE, order.getOpenOrderPrice()))));
        bybitOrderReq.setTakeProfitPrice(String.valueOf(roundToSameDecimal(strategy.getSymbol().getPu(), order.getCurrentTakeProfitPrice())));
        if (strategy.isUseStopLoss()) {
            bybitOrderReq.setStopLossPrice(String.valueOf(roundToSameDecimal(strategy.getSymbol().getPu(), order.getStopLossPrice())));
        }
        String orderLinkId = "2tbot_" + System.currentTimeMillis();
        bybitOrderReq.setOrderLinkId(orderLinkId);
        order.setOrderLinkId(orderLinkId);
        return bybitOrderReq;
    }

    @Override
    public void reduceProfit(Order orderWithUpdatedProfit, Strategy strategy, KlineData marketData) {
        try {
            BybitReduceTpReq reduceTpReq = new BybitReduceTpReq();
            reduceTpReq.setSymbol(orderWithUpdatedProfit.getSymbol());
            reduceTpReq.setOrderId(orderWithUpdatedProfit.getBybitTpOrderId());
            reduceTpReq.setPrice(String.valueOf(roundToSameDecimal(strategy.getSymbol().getPu(), orderWithUpdatedProfit.getCurrentTakeProfitPrice())));

            String path = bybitBaseUrl.concat("order/replace");
            String response = reqRestTemplate(HttpMethod.POST, path, objectMapper.writeValueAsString(reduceTpReq), strategy);
            BybitOrderRes res = objectMapper.readValue(response, BybitOrderRes.class);
            if (res.getRetCode() != 0) {
                logger.error("Error Reduce Take Profit order: {}", res.getRetMsg());
                throw new ReduceTakeProfitFailException(orderWithUpdatedProfit);
            }
            logger.info("REDUCED TAKE PROFIT FOR ORDER {}", orderWithUpdatedProfit.getPlatformOrderId());
        } catch (IOException | URISyntaxException e) {
            throw new ReduceTakeProfitFailException(orderWithUpdatedProfit);
        }
    }

    @Override
    public void syncStatus(Order order, Strategy strategy) throws IOException, URISyntaxException {
        try {
            String path = bybitBaseUrl.concat("order/list?symbol=").concat(order.getSymbol());
            String responseString = reqRestTemplate(HttpMethod.GET, path, "symbol=".concat(order.getSymbol()), strategy);
            BybitGetOrderListResponse response = objectMapper.readValue(responseString, BybitGetOrderListResponse.class);
            BybitGetOrderResponse foundOrder = response.getResult().getList().stream().filter(o -> o.getOrderId().equals(order.getPlatformOrderId())).findFirst().orElse(null);
            if (foundOrder == null) {
                return;
            }

            switch (foundOrder.getOrderStatus()) {
                case Cancelled -> order.setOrderStatus(OrderStatus.IGNORED);
                case Filled -> {
                    order.setPlatformBuyPrice(Double.parseDouble(foundOrder.getPrice()));
                    order.setOrderStatus(OrderStatus.OPEN);
                    order.setRealAmount(Double.parseDouble(foundOrder.getPrice()) * Double.parseDouble(foundOrder.getQty()) * LEVERAGE);
                    List<BybitGetOrderResponse> linkedOrder = new ArrayList<>(response.getResult()
                            .getList()
                            .stream()
                            .filter(a -> a.getCreatedTime() > foundOrder.getCreatedTime())
                            .filter(o -> o.getQty().equals(foundOrder.getQty()))
                            .filter(a -> !a.getCreateType().equals("CreateByUser"))
                            .toList());
                    linkedOrder.sort((a, b) -> a.getCreatedTime() == b.getCreatedTime() ? 0 : (a.getCreatedTime() - b.getCreatedTime() > 0 ? 1 : -1));
                    BybitGetOrderResponse takeProfitOrder = null;
                    BybitGetOrderResponse stopLossOrder = null;
                    if (linkedOrder.isEmpty()) {
                        return;
                    } else {
                        for (int i = 0; i < 2 && i < linkedOrder.size(); i++) {
                            switch (linkedOrder.get(i).getCreateType()) {
                                case "CreateByPartialTakeProfit" -> takeProfitOrder = linkedOrder.get(i);
                                case "CreateByPartialStopLoss" -> stopLossOrder = linkedOrder.get(i);
                                default -> {
                                }
                            }
                        }
                    }

                    if (takeProfitOrder == null) {
                        return;
                    } else {
                        order.setBybitTpOrderId(takeProfitOrder.getOrderId());
                    }
                    if (takeProfitOrder.getOrderStatus().equals(BybitOrderStatus.Filled) || (stopLossOrder != null && stopLossOrder.getOrderStatus().equals(BybitOrderStatus.Filled))) {
                        BybitGetOrderResponse closedOrder = takeProfitOrder.getOrderStatus().equals(BybitOrderStatus.Filled) ? takeProfitOrder : stopLossOrder;
                        order.setOrderStatus(OrderStatus.CLOSED);
                        order.setProfit(getClosedPnl(closedOrder.getOrderId(), strategy));
                        order.setPlatformSellPrice(Double.parseDouble(closedOrder.getPrice()));
                    }
                }
                default -> {
                }
            }

        } catch (Exception e) {
            order.setOrderStatus(OrderStatus.REMOVE_CACHE);
            orderRepo.save(order).block();
            throw e;
        }
    }

    private Double getClosedPnl(String orderId, Strategy strategy) throws IOException, URISyntaxException {
        String path = bybitBaseUrl.concat("position/closed-pnl?symbol=").concat(strategy.getSymbolString());
        String responseString = reqRestTemplate(HttpMethod.GET, path, "symbol=".concat(strategy.getSymbolString()), strategy);
        BybitClosedPnlListResponse closedPnlListResponse = objectMapper.readValue(responseString, BybitClosedPnlListResponse.class);
        BybitClosedPnlResponse closedPnl = closedPnlListResponse.getResult().getList().stream().filter(pnl -> pnl.getOrderId().equals(orderId)).findFirst().orElse(null);
        if (closedPnl == null) {
            throw new RuntimeException("Could not find closed pnl for order: " + orderId);
        }
        return Double.valueOf(closedPnl.getClosedPnl());
    }

    @Override
    public void cancelOrder(Order order, Strategy strategy) throws IOException, URISyntaxException {
        try {
            String path = bybitBaseUrl.concat("order/cancel");
            BybitCancelOrderReq cancelOrderReq = new BybitCancelOrderReq();
            cancelOrderReq.setOrderId(order.getPlatformOrderId());
            cancelOrderReq.setSymbol(order.getSymbol());
            String response = reqRestTemplate(HttpMethod.POST, path, objectMapper.writeValueAsString(cancelOrderReq), strategy);
            BybitOrderRes res = objectMapper.readValue(response, BybitOrderRes.class);
            if (res.getRetCode() != 0) {
                logger.error("Error submitting order: {}", res.getRetMsg());
                throw new RuntimeException(res.getRetMsg());
            }
        } catch (IOException | URISyntaxException e) {
            order.setErrorMessage(e.getLocalizedMessage());
            orderRepo.save(order).block();
            logger.error("CANNOT CANCEL ORDER : {}", order.getPlatformOrderId());
            throw e;
        }
    }

    private static double extractAvailableBalanceBybit(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonObject = mapper.readTree(jsonResponse);
            return jsonObject.get("result").get("list").get(0).get("availableBalance").asDouble();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String reqRestTemplate(HttpMethod method, String path, String payload, Strategy strategy) throws IOException, URISyntaxException {
        LocalDateTime reqTime = LocalDateTime.now();
        long timestamp = System.currentTimeMillis();
        String apiKey = decryptSecretKey(strategy.getBot().getApiKey());
        String secretKey = decryptSecretKey(strategy.getBot().getSecretKey());
        String objectString = String.join("", String.valueOf(timestamp), apiKey, "15000", payload);
        String signature = calculateHmacSHA256(secretKey, objectString);
        String responseString;

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
        httpRequest.setHeader("X-BAPI-SIGN-TYPE", "2");
        httpRequest.setHeader("X-BAPI-SIGN", signature);
        httpRequest.setHeader("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
        httpRequest.setHeader("X-BAPI-RECV-WINDOW", "15000");
        httpRequest.setHeader("X-BAPI-API-KEY", apiKey);

        CloseableHttpResponse response = httpClient.execute(httpRequest);
        int statusCode = response.getStatusLine().getStatusCode();
        responseString = EntityUtils.toString(response.getEntity());

        LocalDateTime respTime = LocalDateTime.now();
        appendLog(path, payload, responseString, reqTime, respTime, strategy.getSymbolString(), strategy.getId(), strategy.getOrderChange(), strategy.getBot().getName());

        if (statusCode >= 400 && statusCode < 500) {
            throw new RuntimeException(responseString);
        }

        return responseString;
    }

    private void appendLog(String url, String reqBody, String resBody, LocalDateTime reqTime, LocalDateTime resTime, String symbol, String strategyId, double strategyOc, String botName) {
        HttpRequestLog log = new HttpRequestLog();
        log.setUrl(url);
        log.setRequestTime(reqTime);
        log.setResponseTime(resTime);
        log.setRequestBody(reqBody);
        log.setResponseBody(resBody);
        log.setSymbolString(symbol);
        log.setStrategyId(strategyId);
        log.setStrategyOc(strategyOc);
        log.setBotName(botName);
        try {
            log.setCurrentBotJson(objectMapper.writeValueAsString(marketContextHolder.getOrder(strategyId)));
        } catch (JsonProcessingException e) {
            logger.error("Error serializing current bot state", e);
        }
        requestLogRepo.save(log).block();
    }
}
