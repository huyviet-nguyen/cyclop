package com.tbot.cyclop.Cyclop.dto;

import com.tbot.cyclop.Cyclop.model.Order;
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
    private String action;
    private OrderStatus orderStatus;
    private Strategy strategy;
    private String strategyShort;
    private String status;
    private double price;
    private double amount;
    private double orderAmount;
    private double balance;
    private double profit;

    public String getDecoratedAmount() {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        String formattedValue = decimalFormat.format(amount);
        formattedValue = "$" + formattedValue;
        return formattedValue;
    }
    public String getDecoratedProfit() {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        String formattedValue = decimalFormat.format(profit);
        formattedValue = "$" + formattedValue;
        return formattedValue;
    }

    public static NotificationPayload fromOrderAck(Order order) {
        NotificationPayload notificationPayload = new NotificationPayload();
        notificationPayload.setSymbol(order.getSymbol());
        notificationPayload.setPlatform(order.getPlatform());
        notificationPayload.setBotName(order.getStrategy().getBot().getName());
        notificationPayload.setAction(order.getOrderStatus().toString().replace("_", " "));
        notificationPayload.setStrategyShort(order.getStrategy().toNotiString());
        notificationPayload.setAmount(order.getVolume() * order.getEntryPrice() / 10);
        notificationPayload.setPrice(order.getEntryPrice());
        return notificationPayload;
    }
}
