package com.tbot.cyclop.Cyclop.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Data
@Document(collection = "order")
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
    @DocumentReference
    private Strategy strategy;
    private LocalDateTime createdAt;
    private long platformTimestamp;
    private LocalDateTime updatedAt;
    private String platformOrderId;
    private double profit;

    public String getSymbolWithUnderScore() {
        return strategy.getSymbol().getSymbol();
    }

    public double getCurrentTakeProfitPercent() {
        return Math.abs(((currentTakeProfitPrice - openOrderPrice) / openOrderPrice) * 100);
    }
}
