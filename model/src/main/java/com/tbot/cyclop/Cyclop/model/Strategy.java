package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "strategy")
public class Strategy {
    @Id
    private String id;
    private int orderChange;
    private int extendOrderChangePercent;
    private int takeProfit;
    private int stopLoss;
    private int reduceTakeProfit;
    private int amount;
    private int ignore;
    private String platform;
    private String status;
    private String candleStick;
    private String positionSide;
    private Symbol symbol;
    private User user;
    private Bot bot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

