package com.tbot.cyclop.Cyclop.dto;

import com.tbot.cyclop.Cyclop.model.OrderAckHistory;
import com.tbot.cyclop.Cyclop.model.OrderAction;
import com.tbot.cyclop.Cyclop.model.Strategy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.DecimalFormat;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {
    private String symbol;
    private String botName;
    private String platform;
    private OrderAction orderAction;
    private Strategy strategy;
    private String strategyShort;
    private String status;
    private double price;
    private double amount;
    private double orderAmount;
    private String apiKey;
    private String apiSecret;

    public String getDecoratedAmount() {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        String formattedValue = decimalFormat.format(amount);
        formattedValue = "$" + formattedValue;
        return formattedValue;
    }

    public static NotificationPayload fromOrderAck(OrderAckHistory orderAckHistory){
        NotificationPayload notificationPayload = new NotificationPayload();
        notificationPayload.setBotName(orderAckHistory.getStrategy().getBot().getName());
        notificationPayload.setSymbol(orderAckHistory.getSymbol());
        notificationPayload.setPrice(orderAckHistory.getPrice());
        notificationPayload.setOrderAmount(orderAckHistory.getAmount());
        notificationPayload.setStrategyShort(orderAckHistory.getStrategy().toNotiString());
        notificationPayload.setOrderAction(orderAckHistory.getOrderAction());
        notificationPayload.setPlatform(orderAckHistory.getPlatform());
        notificationPayload.setApiKey(orderAckHistory.getApiKey());
        notificationPayload.setApiSecret(orderAckHistory.getApiSecret());
        return notificationPayload;
    }
}
