package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.NotificationPayload;
import com.tbot.cyclop.Cyclop.dto.req.TelegramNotiPayload;
import com.tbot.cyclop.Cyclop.model.Bot;
import com.tbot.cyclop.Cyclop.model.Strategy;
import com.tbot.cyclop.Cyclop.model.TelegramBotInfo;
import com.tbot.cyclop.Cyclop.model.User;
import com.tbot.cyclop.orderplacer.repo.TelegramBotInfoRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.util.*;


@Component
public class TelegramService {

    private final TelegramBotInfoRepo infoRepo;

    private final Logger logger = LoggerFactory.getLogger(TelegramService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_CHANNEL_ID = "-1002026702728";

    private static final String NOTIFICATION_TEMPLATE =
            """
                    *%s *     | %s
                    *Bot*     : %s
                    *Strategy*: %s
                    *Price*   : %s | *Amount*: %s
                    """;
    private static final String TAKE_PROFIT = "\n*Profit* : %s";
    private static final String LOSS = "\n*Loss* : %s";


    public TelegramService(TelegramBotInfoRepo infoRepo) {
        this.infoRepo = infoRepo;
    }

    private String getUrl(String token) {
        return String.format("https://api.telegram.org/bot%s/sendMessage", token);
    }

    private String getTextNotificationPayload(NotificationPayload notificationPayload) {
        if (notificationPayload.getAction().equals("TOOK PROFIT")) {
            return String.format(NOTIFICATION_TEMPLATE.concat(TAKE_PROFIT),
                    notificationPayload.getSymbol(),
                    notificationPayload.getAction(),
                    notificationPayload.getBotName(),
                    notificationPayload.getStrategyShort(),
                    notificationPayload.getPrice(),
                    notificationPayload.getDecoratedAmount(),
                    notificationPayload.getDecoratedProfit());
        }
        if (notificationPayload.getAction().equals("STOPPED LOSS")) {
            return String.format(NOTIFICATION_TEMPLATE.concat(LOSS),
                    notificationPayload.getSymbol(),
                    notificationPayload.getAction(),
                    notificationPayload.getBotName(),
                    notificationPayload.getStrategyShort(),
                    notificationPayload.getPrice(),
                    notificationPayload.getDecoratedAmount(),
                    notificationPayload.getDecoratedProfit());
        }
        return String.format(NOTIFICATION_TEMPLATE,
                notificationPayload.getSymbol(),
                notificationPayload.getAction(),
                notificationPayload.getBotName(),
                notificationPayload.getStrategyShort(),
                notificationPayload.getPrice(),
                notificationPayload.getDecoratedAmount());

    }


    public void sendNotification(Strategy strategy, NotificationPayload payload) throws JsonProcessingException {
        TelegramBotInfo botInfo = infoRepo.findAll().blockFirst();
        Bot bot = strategy.getBot();
        User user = strategy.getUser();
        if (botInfo == null || botInfo.getApiToken() == null || bot == null || bot.getApiKey() == null || bot.getSecretKey() == null || user == null) {
            logger.error("Cannot send noti for" + getTextNotificationPayload(payload));
            return;
        }
        TelegramNotiPayload telegramNotiPayload = new TelegramNotiPayload();
        telegramNotiPayload.setText(getTextNotificationPayload(payload));
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



}
