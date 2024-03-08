package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data
@Document(collection = "candle_window")
public class CandleWindow {
    @Id
    private String id;
    private String platform;
    private String symbol;
    private double openPrice;
    private String interval;
}
