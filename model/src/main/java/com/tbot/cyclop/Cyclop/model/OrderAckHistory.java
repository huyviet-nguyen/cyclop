package com.tbot.cyclop.Cyclop.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Data
@Document(collection = "order_ack")
@AllArgsConstructor
@NoArgsConstructor
public class OrderAckHistory {

    @Id
    private String id; //
    private String platform; //
    private double entryPrice; //
    private double takeProfitPrice;
    private double stopLossPrice;
    private long timestamp; //
    private OrderStatus orderStatus; //
    private String platformOrderStatus;
    private String symbol; //
    private double candleOpenPrice; //
    @DocumentReference
    private Strategy strategy; //
    private LocalDateTime createdAt; //
    private long createdOnPlatformAt;
    private LocalDateTime updatedAt; //
    private String platformOrderId;
    public String getSymbolWithUnderScore() {
        return strategy.getSymbol().getSymbol();
    }


}
