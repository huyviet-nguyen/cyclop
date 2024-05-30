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
import com.tbot.cyclop.orderplacer.repo.HttpRequestLogRepo;
import com.tbot.cyclop.orderplacer.repo.OrderRepo;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private void appendLog(String url, String reqBody, String resBody, LocalDateTime reqTime, LocalDateTime resTime) {
        HttpRequestLog log = new HttpRequestLog();
        log.setUrl(url);
        log.setRequestTime(reqTime);
        log.setResponseTime(resTime);
        log.setRequestBody(reqBody);
        log.setResponseBody(resBody);
        requestLogRepo.save(log).block();
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
            logger.info(objectMapper.writeValueAsString(openOrderRequest));
            try {
                LocalDateTime reqTime = LocalDateTime.now();
                String responseString =
                        webClient.post()
                                .uri(path)
                                .body(BodyInserters.fromValue(openOrderRequest))
                                .header("Content-Type", "application/json")
                                .header("Content-Length", contentLength)
                                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                                .header("X-Mxc-Sign", headerHash)
                                .header("Authorization", webToken)
                                .retrieve()
                                .bodyToMono(String.class).block();
                LocalDateTime resTime = LocalDateTime.now();
                appendLog(path, objectMapper.writeValueAsString(openOrderRequest), responseString, reqTime, resTime);
                MexcOrderResponse response = objectMapper.readValue(responseString, MexcOrderResponse.class);
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
            throw new OpenOrderFailException("BALANCE NOT ENOUGH");
        }
    }

    @Override
    public void reduceProfit(Order orderWithUpdatedProfit, Strategy strategy, KlineData marketData) throws JsonProcessingException {
        String webToken = decryptSecretKey(strategy.getBot().getWebToken());
        MexcStopOrderListResponse stopOrderList = getStopOrderOpenOrders(webToken, strategy.getSymbolString());
        MexcStopOrderResponse stopOrder = stopOrderList.getData().stream().filter(a -> Objects.equals(a.getOrderId(), orderWithUpdatedProfit.getOpenedOrderId())).findFirst().orElse(null);
        if (stopOrder == null) {
            logger.error("CANNOT FIND OPENED ORDER {} ON PLATFORM, CANNOT REDUCE TAKE-PROFIT", orderWithUpdatedProfit.getPlatformOrderId());
            String foundId = stopOrderList.getData().stream().map(MexcStopOrderResponse::getOrderId).collect(Collectors.joining(","));
            throw new ReduceTakeProfitFailException(orderWithUpdatedProfit, foundId);
        }
        MexcChangePriceRequest changePriceRequest = getMexcChangePriceRequest(orderWithUpdatedProfit, stopOrder, strategy);
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/stoporder/change_plan_order");
        String stringPayload = objectMapper.writeValueAsString(changePriceRequest);
        String contentLength = String.valueOf(stringPayload.getBytes(StandardCharsets.UTF_8).length);
        String headerHash = getMexcSign(stringPayload, timestamp, webToken);
        MexcChangeOrderResponse response;
        LocalDateTime reqTime = LocalDateTime.now();
        String stringResponse = webClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(changePriceRequest))
                .header("Content-Type", "application/json")
                .header("Content-Length", contentLength)
                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                .header("X-Mxc-Sign", headerHash)
                .header("Authorization", webToken)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))  // Add timeout here
//                .onErrorResume(Exception.class, ex -> Mono.empty()) // Return null on timeout
                .block();
        LocalDateTime resTime = LocalDateTime.now();
        appendLog(path, objectMapper.writeValueAsString(changePriceRequest), stringResponse, reqTime, resTime);
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
        MexcOrderHistoryListResponse listResponse = null;
        Optional<MexcStopOrderResponse> closedFound = Optional.empty();

        if (OrderStatus.SUBMIT.equals(order.getOrderStatus())) {
            listResponse = getOrderListHistoryOrder(decryptedWebToken, strategy.getSymbolString());
            Optional<MexcOrderHistoryListResponse.MexcOrderHistoryResponse> openedFound =
                    listResponse.getData().stream()
                            .filter(res -> res.getExternalOid().contains(order.getPlatformOrderId())
                            ).findFirst();
            if (openedFound.isPresent()) {
                if (openedFound.get().getState() != 4) {
                    order.setOrderStatus(OrderStatus.OPEN);
                    order.setOpenedOrderId(openedFound.get().getOrderId());
                } else {
                    order.setOrderStatus(OrderStatus.IGNORED);
                }
                return;
            }
        }

        if (OrderStatus.OPEN.equals(order.getOrderStatus())) {
            closedFound = getStopOrderListOrder(decryptedWebToken, strategy.getSymbolString()).getData().stream()
                    .filter(res -> res.getOrderId().equals(order.getOpenedOrderId()) && res.getState() == 3 && res.getPositionId() != null).findFirst();
            if (closedFound.isPresent()) {
                order.setPositionId(closedFound.get().getPositionId());
                MexcPositionListResponse positionListResponse = getClosedPositionHistory(decryptedWebToken, strategy.getSymbolString());
                Optional<MexcStopOrderResponse> finalClosedFound = closedFound;
                MexcPositionResponse position = positionListResponse.getData().stream().filter(
                        pos -> pos.getPositionId() == Long.parseLong(finalClosedFound.get().getPositionId())
                ).findFirst().orElse(null);

                if (position != null) {
                    order.setOrderStatus(OrderStatus.CLOSED);
                    order.setProfit(position.getCloseProfitLoss());
                    return;
                } else {
                    order.setOrderStatus(OrderStatus.IGNORED);
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
            LocalDateTime start = LocalDateTime.now();
            String responseString = webClient.post()
                    .uri(path)
                    .header("Content-Type", "application/json")
                    .header("X-Mxc-Nonce", String.valueOf(timestamp))
                    .header("X-Mxc-Sign", headerHash)
                    .header("Content-Length", contentLength)
                    .header("Authorization", decryptWebToken)
                    .body(BodyInserters.fromValue(payload))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            LocalDateTime end = LocalDateTime.now();
            appendLog(path,payload, responseString, start, end);
            logger.info("CANCEL ORDER : {}", responseString);
            order.setOrderStatus(OrderStatus.IGNORED);
        } catch (Exception e) {
            orderRepo.save(order).block();
            logger.error("CANNOT CANCEL ORDER : {}", order.getPlatformOrderId());
            throw e;
        }
    }

    private MexcOrderHistoryListResponse getOrderListHistoryOrder(String decryptedWebToken, String symbol) throws JsonProcessingException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/order/list/history_orders?category=1,6&page_num=1&page_size=50&symbol=").concat(symbol);
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);
        LocalDateTime now = LocalDateTime.now();
        String responseString = webClient.get()
                .uri(path)
                .header("Content-Type", "application/json")
                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                .header("X-Mxc-Sign", headerHash)
                .header("Authorization", decryptedWebToken)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))  // Add timeout here
                .block();
        LocalDateTime end = LocalDateTime.now();
        appendLog(path, "", responseString, now, end);
        return objectMapper.readValue(responseString, MexcOrderHistoryListResponse.class);
    }

    private MexcPositionListResponse getClosedPositionHistory(String decryptedWebToken, String symbol) throws JsonProcessingException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/position/list/history_positions?page_num=1&page_size=20&symbol=").concat(symbol);
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);
        LocalDateTime now = LocalDateTime.now();
        String responseString = webClient.get()
                .uri(path)
                .header("Content-Type", "application/json")
                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                .header("X-Mxc-Sign", headerHash)
                .header("Authorization", decryptedWebToken)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))
                .block();
        LocalDateTime end = LocalDateTime.now();
        appendLog(path, "", responseString, now, end);
        return objectMapper.readValue(responseString, MexcPositionListResponse.class);
    }

    private MexcStopOrderListResponse getStopOrderOpenOrders(String decryptedWebToken, String symbol) {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/stoporder/open_orders?");
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);

        return webClient.get()
                .uri(path)
                .header("Content-Type", "application/json")
                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                .header("X-Mxc-Sign", headerHash)
                .header("Authorization", decryptedWebToken)
                .retrieve()
                .bodyToMono(MexcStopOrderListResponse.class)
                .timeout(Duration.ofSeconds(4))  // Add timeout here
                .block();
    }

    private MexcStopOrderListResponse getStopOrderListOrder(String decryptedWebToken, String symbol) throws JsonProcessingException {
        long timestamp = System.currentTimeMillis();

        String path = mexcOrderBaseUrl.concat("api/v1/private/stoporder/list/orders?is_finished=1&page_num=1&page_size=20&symbol=").concat(symbol);
        String headerHash = getMexcSign("", timestamp, decryptedWebToken);
        LocalDateTime requestTime = LocalDateTime.now();
        String responseString = webClient.get()
                .uri(path)
                .header("Content-Type", "application/json")
                .header("X-Mxc-Nonce", String.valueOf(timestamp))
                .header("X-Mxc-Sign", headerHash)
                .header("Authorization", decryptedWebToken)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))  // Add timeout here
                .block();
        LocalDateTime responseTime = LocalDateTime.now();
        appendLog(path, "", responseString, requestTime, responseTime);
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

    @Transactional
    public MexcOpenOrderRequest orderAckToMexcOpenOrderRequest(Order sysOrder, Strategy strategy) throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        double pu = strategy.getSymbol().getPu();
        MexcOpenOrderRequest mexcOrder = new MexcOpenOrderRequest();
        String side = strategy.getPositionSide().equals("LONG") ? "1" : "3";
        String triggerType = strategy.getPositionSide().equals("LONG") ? "2" : "1";
        byte[] key = generateRandomBytes(32);
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
        double cont = strategy.getSymbol().getCs() * sysOrder.getOpenOrderPrice();
        int volume = getVolume(balance, strategy.getRealAmount(), cont);
        logger.info("ACCOUNT {} | BALANCE : {} | VOL {}", strategy.getBot().getName(), balance, volume);
        mexcOrder.setVol(volume);
        sysOrder.setVolume(volume);
        sysOrder.setOpenOrderPrice(Double.parseDouble(mexcOrder.getTriggerPrice()));
        sysOrder.setCurrentTakeProfitPrice(Double.parseDouble(mexcOrder.getTakeProfitPrice()));
        sysOrder.setStopLossPrice(Double.parseDouble(mexcOrder.getStopLossPrice()));
        return mexcOrder;
    }
}
