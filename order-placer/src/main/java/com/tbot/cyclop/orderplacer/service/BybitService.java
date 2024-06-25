package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.req.bybit.BybitCancelOrderReq;
import com.tbot.cyclop.Cyclop.dto.req.bybit.BybitOrderReq;
import com.tbot.cyclop.Cyclop.dto.res.bybit.BybitOrderRes;
import com.tbot.cyclop.Cyclop.model.HttpRequestLog;
import com.tbot.cyclop.Cyclop.model.Order;
import com.tbot.cyclop.Cyclop.model.OrderStatus;
import com.tbot.cyclop.Cyclop.model.Strategy;
import com.tbot.cyclop.orderplacer.exception.OpenOrderFailException;
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

import static com.tbot.cyclop.Cyclop.HttpConstant.APPLICATION_JSON;
import static com.tbot.cyclop.Cyclop.HttpConstant.CONTENT_TYPE_HEADER_NAME;
import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.calculateHmacSHA256;
import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.decryptSecretKey;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.getBybitQuantity;

@Service
public class BybitService implements PlatformService {

    @Value("${bybit.contract.api.baseUrl}")
    public String bybitBaseUrl;

    private final Logger logger = LoggerFactory.getLogger(BybitService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpRequestLogRepo requestLogRepo;

    private final OrderRepo orderRepo;

    public BybitService(HttpRequestLogRepo requestLogRepo, OrderRepo orderRepo) {
        this.requestLogRepo = requestLogRepo;
        this.orderRepo = orderRepo;
    }

    @Override
    public double getBalance(String apiKey, String apiSecret) {
        String path = bybitBaseUrl.concat("wallet/balance?coin=USDT");
        long timestamp = System.currentTimeMillis();
        String objectString = String.join("", String.valueOf(timestamp), apiKey, "5000", "coin=USDT");
        String signature = calculateHmacSHA256(apiSecret, objectString);
        WebClient client = WebClient.create();
        return client.method(HttpMethod.GET)
                .uri(path)
                .header("X-BAPI-SIGN-TYPE", "2")
                .header(CONTENT_TYPE_HEADER_NAME, APPLICATION_JSON)
                .header("X-BAPI-API-KEY", apiKey)
                .header("X-BAPI-SIGN", signature)
                .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                .header("X-BAPI-RECV-WINDOW", "5000")
                .retrieve()
                .bodyToMono(String.class).doOnError(res -> logger.error(res.getMessage())).map(BybitService::extractAvailableBalanceBybit).block();
    }



    @Override
    public void submitOrder(Order newOrder, Strategy strategy) {
        String apiKey = decryptSecretKey(strategy.getBot().getApiKey());
        String secretKey = decryptSecretKey(strategy.getBot().getSecretKey());
        double balance = getBalance(apiKey, secretKey);

        BybitOrderReq bybitOrderReq = mapToBybitOrder(newOrder, strategy, balance);
        try{
            String response = reqRestTemplate(HttpMethod.POST, bybitBaseUrl.concat("order/create"),objectMapper.writeValueAsString(bybitOrderReq), strategy);
            BybitOrderRes res = objectMapper.readValue(response, BybitOrderRes.class);
            if (res.getRetCode() != 0) {
                logger.error("Error submitting order: {}", res.getRetMsg());
                throw new OpenOrderFailException(res.getRetMsg());
            }
            newOrder.setPlatformOrderId(String.valueOf(res.getResult().getOrderId()));
            logger.info("SUBMIT ORDER {} ON {} SYMBOL {}", newOrder.getPlatformOrderId(), newOrder.getPlatform(), newOrder.getSymbol());
            newOrder.setPlatformTimestamp(System.currentTimeMillis());
            newOrder.setOrderStatus(OrderStatus.SUBMIT);
        } catch (IOException | URISyntaxException e) {
            throw new OpenOrderFailException(newOrder, e);
        }
    }

    private BybitOrderReq mapToBybitOrder(Order order,Strategy strategy, double balance){
        BybitOrderReq bybitOrderReq = new BybitOrderReq();
        if (strategy.getPositionSide().equals("LONG")){
            bybitOrderReq.setSide("Buy");
            bybitOrderReq.setTriggerDirection(2);
        } else {
            bybitOrderReq.setSide("Sell");
            bybitOrderReq.setTriggerDirection(1);
        }
        bybitOrderReq.setPrice(String.valueOf(order.getOpenOrderPrice()));
        bybitOrderReq.setTriggerPrice(String.valueOf(order.getOpenOrderPrice()));
        bybitOrderReq.setQuantity(String.valueOf(getBybitQuantity(balance,strategy.getRealAmount(),10, order.getOpenOrderPrice())));
        bybitOrderReq.setTakeProfitPrice(String.valueOf(order.getCurrentTakeProfitPrice()));
        bybitOrderReq.setStopLossPrice(String.valueOf(order.getStopLossPrice()));
        String orderLinkId = "2tbot_" + System.currentTimeMillis();
        bybitOrderReq.setOrderLinkId(orderLinkId);
        order.setOrderLinkId(orderLinkId);
        return bybitOrderReq;
    }

    @Override
    public void reduceProfit(Order orderWithUpdatedProfit, Strategy strategy, KlineData marketData) {

    }

    @Override
    public void syncStatus(Order order, Strategy strategy) {

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
            return jsonObject.get("result").get("list").get(0).get("walletBalance").asDouble();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String reqRestTemplate(HttpMethod method, String path, String payload, Strategy strategy) throws IOException, URISyntaxException {
        LocalDateTime reqTime = LocalDateTime.now();
        long timestamp = System.currentTimeMillis();
        String apiKey = decryptSecretKey(strategy.getBot().getApiKey());
        String secretKey = decryptSecretKey(strategy.getBot().getSecretKey());
        String objectString = String.join("", String.valueOf(timestamp), apiKey, "5000", "coin=USDT");
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
        httpRequest.setHeader("X-BAPI-RECV-WINDOW", "5000");

        CloseableHttpResponse response = httpClient.execute(httpRequest);
        int statusCode = response.getStatusLine().getStatusCode();
        responseString = EntityUtils.toString(response.getEntity());

        LocalDateTime respTime = LocalDateTime.now();
        appendLog(path, payload, responseString, reqTime, respTime, strategy.getSymbolString(), strategy.getId());

        if (statusCode >= 400 && statusCode < 500) {
            throw new RuntimeException(responseString);
        }

        return responseString;
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
}
