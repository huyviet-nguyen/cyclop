package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.req.TelegramNotiPayload;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.repo.TelegramBotInfoRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.util.*;


@Component
public class NotificationService {

    private final TelegramBotInfoRepo infoRepo;

    private final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_CHANNEL_ID = "-1002026702728";


    private static final String OPEN_ORDER_NOTIFICATION_TEMPLATE =
            """
                    *Coin:* %s| OPEN %s
                    *Bot*     : %s
                    *Strategy*:
                    %s
                    *Price*   : %s
                    *Amount*: %s$
                    """;

    private static final String TAKE_PROFIT_ORDER_NOTIFICATION_TEMPLATE =
            """
                    *Coin:* %s| TAKE PROFIT
                    *Bot*     : %s
                    *Strategy*:
                    %s
                    *Price*   : %s
                    *Profit*: %s
                    """;
    private static final String STOP_LOSS_ORDER_NOTIFICATION_TEMPLATE =
            """
                    *Coin:* %s| STOP LOSS
                    *Bot*     : %s
                    *Strategy*:
                    %s
                    *Price*   : %s
                    *Loss*: %s
                    """;

    public NotificationService(TelegramBotInfoRepo infoRepo) {
        this.infoRepo = infoRepo;
    }

    private String getUrl(String token) {
        return String.format("https://api.telegram.org/bot%s/sendMessage", token);
    }

    private String getNotificationContent(Order order, Strategy strategy) {
        return switch (order.getOrderStatus()) {
            case OPEN -> String.format(OPEN_ORDER_NOTIFICATION_TEMPLATE,
                    order.getSymbol().replace("_", " "),
                    strategy.getPositionSide(),
                    strategy.getBot().getName(),
                    strategy.toNotiString(),
                    order.getOpenOrderPrice(),
                    order.getVolume() * order.getOpenOrderPrice());
            case STOPPED_LOSS -> String.format(STOP_LOSS_ORDER_NOTIFICATION_TEMPLATE,
                    order.getSymbol().replace("_", " "),
                    strategy.getBot().getName(),
                    strategy.toNotiString(),
                    order.getOpenOrderPrice(),
                    order.getProfit());
            case TOOK_PROFIT -> String.format(TAKE_PROFIT_ORDER_NOTIFICATION_TEMPLATE,
                    order.getSymbol().replace("_", " "),
                    strategy.getBot().getName(),
                    strategy.toNotiString(),
                    order.getOpenOrderPrice(),
                    order.getProfit());
            default -> "";
        };
    }


    public void sendNotification(Order order, Strategy strategy) throws JsonProcessingException {
        TelegramBotInfo botInfo = infoRepo.findAll().blockFirst();
        Bot bot = strategy.getBot();
        User user = strategy.getUser();
        if (botInfo == null || botInfo.getApiToken() == null || bot == null || bot.getApiKey() == null || bot.getSecretKey() == null || user == null) {
            logger.error("Cannot send noti for" + order.getId());
            return;
        }
        TelegramNotiPayload telegramNotiPayload = new TelegramNotiPayload();
        String content = getNotificationContent(order, strategy);
        if (content.isEmpty()) {
            return;
        }
        telegramNotiPayload.setText(content);
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
                .bodyToMono(String.class)
                .doOnError(e -> logger.error(e.getLocalizedMessage())).block();

        logger.info("SENT NOTIFICATION TO " + user.getName() + " at " + user.getTelegramId());
    }


}
