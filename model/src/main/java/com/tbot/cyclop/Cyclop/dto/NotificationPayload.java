package com.tbot.cyclop.Cyclop.dto;

import com.tbot.cyclop.Cyclop.model.OrderAckHistory;
import com.tbot.cyclop.Cyclop.model.OrderStatus;
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
    private OrderStatus orderStatus;
    private Strategy strategy;
    private String strategyShort;
    private String status;
    private double price;
    private double amount;
    private double orderAmount;
    private double balance;

    public String getDecoratedAmount() {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        String formattedValue = decimalFormat.format(amount);
        formattedValue = "$" + formattedValue;
        return formattedValue;
    }
    public String getDecoratedBalance() {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        String formattedValue = decimalFormat.format(balance);
        formattedValue = "$" + formattedValue;
        return formattedValue;
    }

    public static NotificationPayload fromOrderAck(OrderAckHistory orderAckHistory){
        NotificationPayload notificationPayload = new NotificationPayload();
        notificationPayload.setBotName(orderAckHistory.getStrategy().getBot().getName());
        notificationPayload.setSymbol(orderAckHistory.getSymbol());
        notificationPayload.setPrice(orderAckHistory.getEntryPrice());
        notificationPayload.setOrderAmount(orderAckHistory.getUsdtAmount());
        notificationPayload.setStrategyShort(orderAckHistory.getStrategy().toNotiString());
        notificationPayload.setOrderStatus(orderAckHistory.getOrderStatus());
        notificationPayload.setPlatform(orderAckHistory.getPlatform());
        return notificationPayload;
    }
}
