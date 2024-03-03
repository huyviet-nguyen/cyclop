package com.tbot.cyclop.Cyclop.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "order_ack")
@AllArgsConstructor
@NoArgsConstructor
public class OrderAckHistory {

    @Id
    private String id;
    private String platform;
    private String apiKey;
    private double price;
    private String strategyId;
    private String timestamp;
    private double amount;
    private OrderAction orderAction;
    private String symbol;
    private String userId;

    public OrderAckHistory(String platform, String apiKey, double price, String strategyId, String timestamp, double amount, OrderAction orderAction, String symbol, String userId) {
        this.platform = platform;
        this.apiKey = apiKey;
        this.price = price;
        this.strategyId = strategyId;
        this.timestamp = timestamp;
        this.amount = amount;
        this.orderAction = orderAction;
        this.symbol = symbol;
        this.userId = userId;
    }
}
