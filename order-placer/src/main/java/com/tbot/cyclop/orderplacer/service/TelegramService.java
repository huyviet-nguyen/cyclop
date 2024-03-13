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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

import static com.tbot.cyclop.orderplacer.service.GenericHttpUtil.calculateHmacSHA256;
import static com.tbot.cyclop.orderplacer.service.GenericHttpUtil.decryptSecretKey;


@Component
public class TelegramService {

    private final TelegramBotInfoRepo infoRepo;

    private final Logger logger = LoggerFactory.getLogger(TelegramService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_CHANNEL_ID = "-1002112543276";

    private static final HashMap<String, String> BASE_URL_MAP = new HashMap<>();

    static {
        BASE_URL_MAP.put("MEXC", "https://contract.mexc.com/api/v1/");
        BASE_URL_MAP.put("BYBIT", "");
    }

    WebClient webClient = WebClient.builder().build();


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
                "Price   : %s, amount: [Not Available Yet]]\n" +
                "Balance : %s";
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
            amount = getFutureBalance(decryptSecretKey(payload.getApiKey()), decryptSecretKey(payload.getApiSecret()), payload.getPlatform()) * payload.getOrderAmount() / 100;
        } catch (Exception e){
            logger.error(e.getMessage());
        }
        payload.setAmount(amount);
        TelegramNotiPayload telegramNotiPayload = new TelegramNotiPayload();
        telegramNotiPayload.setText(getTextNotiPayload(payload));
        telegramNotiPayload.setChatId(Optional.ofNullable(user.getTelegramId()).orElse(DEFAULT_CHANNEL_ID));
        telegramNotiPayload.setDisableNotification(false);

        WebClient client = WebClient.builder()
                .baseUrl(getUrl(botInfo.getApiToken()))
                .build();

        client.post()
                .uri(UriBuilder::build)
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(objectMapper.writeValueAsString(telegramNotiPayload)))
                .retrieve()
                .bodyToMono(String.class).block();
        logger.info("SENT NOTIFICATION TO " + user.getName() + " at " + user.getTelegramId());
    }

    public double getFutureBalance(String apiKey, String apiSecret, String platform) throws JsonProcessingException {
        String path = BASE_URL_MAP.get(platform).concat("private/account/asset/USDT");
        long timestamp = System.currentTimeMillis();
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("currency", "USDT");
        String objectString = String.join("", apiKey, String.valueOf(timestamp), buildQueryString(requestBody));
        String signature = calculateHmacSHA256(objectString, apiSecret);
        WebClient client = WebClient.create();
        return client.method(HttpMethod.GET)
                .uri(path)
                .header("Content-Type", "application/json")
                .header("ApiKey", apiKey)
                .header("Signature", signature)
                .header("Request-Time", String.valueOf(timestamp))
                .retrieve()
                .bodyToMono(String.class).map(TelegramService::extractAvailableBalance).block();
    }

    public static double extractAvailableBalance(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            JsonNode dataNode = rootNode.get("data");
            JsonNode availableBalanceNode = dataNode.get("availableBalance");
            return availableBalanceNode.asDouble();
        } catch (Exception e) {
            throw new RuntimeException("Error extracting available balance: " + e.getMessage(), e);
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

        try {
            return key + "=" + URLEncoder.encode(valueString, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }


}
