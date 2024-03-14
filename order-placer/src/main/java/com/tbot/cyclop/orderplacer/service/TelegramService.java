package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.NotificationPayload;
import com.tbot.cyclop.Cyclop.dto.TelegramNotiPayload;
import com.tbot.cyclop.Cyclop.model.TelegramBotInfo;
import com.tbot.cyclop.Cyclop.model.User;
import com.tbot.cyclop.orderplacer.repo.TelegramBotInfoRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.tbot.cyclop.orderplacer.service.GenericHttpUtil.calculateHmacSHA256;
import static com.tbot.cyclop.orderplacer.service.GenericHttpUtil.decryptSecretKey;


@Component
public class TelegramService {

    private final TelegramBotInfoRepo infoRepo;

    private final Logger logger = LoggerFactory.getLogger(TelegramService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_CHANNEL_ID = "-1002026702728";

    private static final HashMap<String, String> URL_MAP = new HashMap<>();

    static {
        URL_MAP.put("MEXC", "https://contract.mexc.com/api/v1/private/account/asset/USDT");
        URL_MAP.put("BYBIT", "https://api.bybit.com/contract/v3/private/account/wallet/balance?coin=USDT");
    }


    public TelegramService(TelegramBotInfoRepo infoRepo) {
        this.infoRepo = infoRepo;
    }

    private String getUrl(String token) {
        return String.format("https://api.telegram.org/bot%s/sendMessage", token);
    }

    private String getTextNotiPayload(NotificationPayload notificationPayload) {
        String notiTemplate = "%s | %s \n" +
                "Bot     : %s\n" +
                "Strategy: %s\n" +
                "Price   : %s | Amount: %s\n";
        return String.format(notiTemplate,
                notificationPayload.getSymbol(),
                notificationPayload.getOrderAction().toString(),
                notificationPayload.getBotName(),
                notificationPayload.getStrategyShort(),
                notificationPayload.getPrice(),
                notificationPayload.getDecoratedAmount());
    }


    public void sendNotification(User user, NotificationPayload payload) throws JsonProcessingException {
        TelegramBotInfo botInfo = infoRepo.findAll().blockFirst();
        if (botInfo == null || botInfo.getApiToken() == null) {
            logger.error("Cannot send noti for" + getTextNotiPayload(payload));
            return;
        }
        double amount = 0;
        try {
            amount = getBalance(decryptSecretKey(payload.getApiKey()), decryptSecretKey(payload.getApiSecret()), payload.getPlatform()) * payload.getOrderAmount() / 100;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        payload.setAmount(amount);
        TelegramNotiPayload telegramNotiPayload = new TelegramNotiPayload();
        telegramNotiPayload.setText(getTextNotiPayload(payload));
        telegramNotiPayload.setChatId(Optional.ofNullable(user.getTelegramId()).orElse(DEFAULT_CHANNEL_ID));
        telegramNotiPayload.setDisableNotification(false);
        logger.info("SENT NOTIFICATION TO " + user.getName() + " at " + user.getTelegramId());
        WebClient client = WebClient.builder()
                .baseUrl(getUrl(botInfo.getApiToken()))
                .build();
        client.post()
                .uri(UriBuilder::build)
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(objectMapper.writeValueAsString(telegramNotiPayload)))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> logger.error(e.getLocalizedMessage())).block();
    }

    private double getBalance(String apiKey, String apiSecret, String platform) {
        switch (platform) {
            case "BYBIT":
                return getBalanceBybit(apiKey, apiSecret);
            case "MEXC":
                return getBalanceMexc(apiKey, apiSecret);
            default:
                return 0;
        }
    }

    public double getBalanceMexc(String apiKey, String apiSecret) {
        String path = URL_MAP.get("MEXC");
        long timestamp = System.currentTimeMillis();
        String objectString = String.join("", apiKey, String.valueOf(timestamp), "coin=USDT");
        String signature = calculateHmacSHA256(apiSecret, objectString);
        WebClient client = WebClient.create();
        return client.method(HttpMethod.GET)
                .uri(path)
                .header("Content-Type", "application/json")
                .header("ApiKey", apiKey)
                .header("Signature", signature)
                .header("Request-Time", String.valueOf(timestamp))
                .retrieve()
                .bodyToMono(String.class).doOnError(res -> logger.error(res.getMessage())).map(TelegramService::extractAvailableBalanceMexc).block();

    }

    public double getBalanceBybit(String apiKey, String apiSecret) {
        String path = URL_MAP.get("BYBIT");
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
                .bodyToMono(String.class).doOnError(res -> logger.error(res.getMessage())).map(TelegramService::extractAvailableBalanceBybit).block();
    }

//    @PostConstruct
//    public void test() {
//        String platform = "BYBIT";
//        String apiKey = "x2ynIaEIPXIWkylrla";
//        String apiSecret = "duMKCtJQPplpB2zBUumZudiZoZcsJPIhiMHA";
//        ;
//        System.out.println(a);
//    }

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

    private static double extractAvailableBalanceBybit(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonObject = mapper.readTree(jsonResponse);
            return jsonObject.get("result").get("list").get(0).get("walletBalance").asDouble();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String buildQueryString(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "";

        List<String> keyValuePairs = new ArrayList<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            keyValuePairs.add(stringifyKeyValuePair(key, value));
        }

        return String.join("&", keyValuePairs);
    }

    private static String stringifyKeyValuePair(String key, Object value) {
        String valueString;
        if (value instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> valueList = (List<String>) value;
            valueString = "[" + String.join(",", valueList) + "]";
        } else {
            valueString = value.toString();
        }
        return key + "=" + URLEncoder.encode(valueString, StandardCharsets.UTF_8);
    }


}
