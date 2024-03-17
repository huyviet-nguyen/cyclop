package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Document(collection = "strategy")
public class Strategy implements Serializable {
    @Id
    private String id;
    private double orderChange;
    private double extendOrderChangePercent;
    private double takeProfit;
    private double stopLoss;
    private double reduceTakeProfit;
    private double amount;
    private double ignore;
    private String platform;
    private String status;
    private String candleStick;
    private String positionSide;
    @DocumentReference
    private Symbol symbol;
    @DocumentReference
    private User user;
    @DocumentReference
    private Bot bot;
    @DocumentReference
    private CandleWindow candleWindow;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String toNotiString() {
        String template = "Futures | %s | OC: %s | TP: %s";
        return String.format(template, candleStick, orderChange, takeProfit);
    }
}

