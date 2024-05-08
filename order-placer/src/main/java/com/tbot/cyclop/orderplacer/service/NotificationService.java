package com.tbot.cyclop.orderplacer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
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

import static com.tbot.cyclop.orderplacer.util.PercentageUtil.*;


@Component
public class NotificationService {

    private final TelegramBotInfoRepo infoRepo;

    private final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_CHANNEL_ID = "-1002026702728";

    private static final String OPEN_ORDER_NOTIFICATION_TEMPLATE =
            """
                    %s| Open%s
                    *Bot* : %s
                    *Strategy*:%s
                    *Open Price* : %s$
                    *Status*: Completed
                    *Price*   : %s$ ,*amount*: %s$
                    """;
    private static final String CLOSE_ORDER_NOTIFICATION_TEMPLATE =
            """
                    %s| Close%s
                    *Bot* : %s
                    *Strategy*:%s
                    *Open Price* : %s$
                    *Status*: Completed
                    *Price*   : %s$ ,*amount*: %s$
                    """;

    private static final String REPORT_NOTIFICATION_TEMPLATE =
            """
                    %s - %s | %s
                    Bot :%s
                    %s WINS , %s LOSES
                    Strategy :PriceExtend | %s%%
                    Futures | %s | OC: %s%% | TP: %s%%
                    Sell price: %s , amount: %s
                    Buy price: %s , amount: %s
                    PNL: $%s %s%%
                    """;

    private static final String ERROR_TEMPLATE =
            """
                    *Bot* :%s
                    *Error* - %s - %s
                    Futures
                    *Price* : %s
                    Summary :Can not create tp order
                    Detail :%s
                    """;

    private String getErrorNotificationContent(Strategy strategy, KlineData klineData, String errorMessage) {
        return String.format(ERROR_TEMPLATE,
                strategy.getBot().getName(),
                strategy.getSymbolString().replace("_", "\\_"), strategy.getPositionSide(),
                klineData.getCurrentPrice(), errorMessage);
    }

    public NotificationService(TelegramBotInfoRepo infoRepo) {
        this.infoRepo = infoRepo;
    }

    private String getUrl(String token) {
        return String.format("https://api.telegram.org/bot%s/sendMessage", token);
    }

    private String getReportNotificationContent(Order order, Strategy strategy, int win, int lose) {
        double pnl;
        double sellPrice;
        if (order.getProfit() > 0) {
            pnl = calculateChangePercent(order.getOpenOrderPrice(), order.getCurrentTakeProfitPrice());
            sellPrice = order.getCurrentTakeProfitPrice();
        } else {
            pnl = calculateChangePercent(order.getOpenOrderPrice(), order.getStopLossPrice());
            sellPrice = order.getStopLossPrice();
        }
        String overall = order.getProfit() > 0 ? "WIN" : "LOSE";
        return String.format(REPORT_NOTIFICATION_TEMPLATE,
                order.getSymbol().replace("_", "\\_"), strategy.getPositionSide(), overall,
                strategy.getBot().getName(),
                win, lose,
                strategy.getExtendOrderChangePercent(),
                strategy.getCandleStick(), strategy.getOrderChange(), strategy.getTakeProfit(),
                sellPrice, strategy.getRealAmount(),
                order.getOpenOrderPrice(), order.getOpenOrderPrice() * order.getVolume(),
                order.getProfit(), pnl);
    }

    private String getNotificationContent(Order order, Strategy strategy) {
        return switch (order.getOrderStatus()) {
            case OPEN -> String.format(OPEN_ORDER_NOTIFICATION_TEMPLATE,
                    order.getSymbol().replace("_", "\\_"),
                    strategy.getPositionSide(),
                    strategy.getBot().getName(),
                    strategy.toNotiString(),
                    order.getCandleOpenPrice(),
                    order.getOpenOrderPrice(),
                    order.getVolume() * order.getOpenOrderPrice());
            case CLOSED -> String.format(CLOSE_ORDER_NOTIFICATION_TEMPLATE,
                    order.getSymbol().replace("_", "\\_"),
                    strategy.getPositionSide(),
                    strategy.getBot().getName(),
                    strategy.toNotiString(),
                    order.getCandleOpenPrice(),
                    order.getOpenOrderPrice(),
                    order.getVolume() * order.getOpenOrderPrice());
            default -> "";
        };
    }

    public void sendReportNotification(Order order, Strategy strategy, int win, int loose) {
        TelegramBotInfo botInfo = infoRepo.findAll().blockFirst();
        Bot bot = strategy.getBot();
        User user = strategy.getUser();
        if (botInfo == null || botInfo.getApiToken() == null || bot == null || bot.getApiKey() == null || bot.getSecretKey() == null || user == null) {
            logger.error("Cannot send noti for" + order.getId());
            return;
        }
        String content = getReportNotificationContent(order, strategy, win, loose);
        try {
            sendNotification(botInfo.getApiToken(), bot.getTelegramId(), content);
        } catch (Exception e) {
            logger.error("Cannot send noti for" + order.getId());
        }
        logger.info("SENT NOTIFICATION TO " + user.getName() + " at " + user.getTelegramId());
    }

    public void sendNotification(Order order, Strategy strategy) {
        TelegramBotInfo botInfo = infoRepo.findAll().blockFirst();
        Bot bot = strategy.getBot();
        User user = strategy.getUser();
        if (botInfo == null || botInfo.getApiToken() == null || bot == null || bot.getApiKey() == null || bot.getSecretKey() == null || user == null) {
            logger.error("Cannot send noti for" + order.getId());
            return;
        }
        String content = getNotificationContent(order, strategy);
        if (content.isEmpty()) {
            return;
        }
        try {
            sendNotification(botInfo.getApiToken(), bot.getTelegramId(), content);
        } catch (Exception e) {
            logger.error("Cannot send noti for" + order.getId());
        }
        logger.info("SENT NOTIFICATION TO " + user.getName() + " at " + user.getTelegramId());
    }

    protected void sendNotification(String telegramBot, String chatId, String content) throws JsonProcessingException {
        TelegramNotiPayload telegramNotiPayload = new TelegramNotiPayload();
        telegramNotiPayload.setText(content);
        telegramNotiPayload.setChatId(Optional.ofNullable(chatId).orElse(DEFAULT_CHANNEL_ID));
        telegramNotiPayload.setDisableNotification(false);
        WebClient client = WebClient.builder()
                .baseUrl(getUrl(telegramBot))
                .build();
        client.post()
                .uri(UriBuilder::build)
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(objectMapper.writeValueAsString(telegramNotiPayload)))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> logger.error(e.getLocalizedMessage())).block();
    }


    public void sendErrorNotification(Strategy strategy, KlineData klineData, String message) {
        TelegramBotInfo botInfo = infoRepo.findAll().blockFirst();
        Bot bot = strategy.getBot();
        User user = strategy.getUser();
        String content = getErrorNotificationContent(strategy, klineData, message);
        try {
            assert botInfo != null;
            sendNotification(botInfo.getApiToken(), bot.getTelegramId(), content);
        } catch (Exception ignored) {
        }
        logger.info("SENT NOTIFICATION TO " + user.getName() + " at " + user.getTelegramId());
    }
}
