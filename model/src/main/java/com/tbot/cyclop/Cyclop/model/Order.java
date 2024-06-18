package com.tbot.cyclop.Cyclop.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "missed_order")
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    @Id
    private String id;
    private String platform;
    private double entryPrice;
    private double openOrderPrice;
    private double currentTakeProfitPrice;
    private double stopLossPrice;
    private long timestamp;
    private OrderStatus orderStatus;
    private String symbol;
    private double candleOpenPrice;
    private int volume;
    private String placedByBotName;
    private LocalDateTime createdAt;
    private long platformTimestamp;
    private LocalDateTime updatedAt;
    private String platformOrderId;
    private double profit;
    private String positionId;
    private double currentActualTakeProfit;
    private String openedOrderId;
    private String botId;
    private String cancelReason;
    private String errorMessage;
    private double platformBuyPrice;
    private double platformSellPrice;
    private double realAmount;

    public String toCancelPayload() {
        String template = "[\"%s\"]";
        return String.format(template,this.getPlatformOrderId());
    }
}
