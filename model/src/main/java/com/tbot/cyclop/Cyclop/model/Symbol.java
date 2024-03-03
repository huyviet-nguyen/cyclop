package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Document(collection = "symbol")
public class Symbol implements Serializable {
    @Id
    private String id;
    private String symbol;
    private String coin;
    private String platform;
    private double amount24;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
