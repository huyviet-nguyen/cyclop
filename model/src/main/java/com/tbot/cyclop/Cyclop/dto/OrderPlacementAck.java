package com.tbot.cyclop.Cyclop.dto;

import lombok.Data;

@Data
public class OrderPlacementAck {
    private String platform;
    private String username;
    private String tokenUsed;
    private double priceAtOrder;
    private String strategyId;
    private String timestamp;
}
