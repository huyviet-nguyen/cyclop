package com.tbot.cyclop.Cyclop.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "order_ack")
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
    private String platformOrderStatus;
    private String symbol;
    private double candleOpenPrice;
    private double volume;
    private String placedByBotName;
    private LocalDateTime createdAt;
    private long platformTimestamp;
    private LocalDateTime updatedAt;
    private String platformOrderId;
    private double profit;

    public double getCurrentTakeProfitPercent() {
        return Math.abs(((currentTakeProfitPrice - openOrderPrice) / openOrderPrice) * 100);
    }
}
