package com.tbot.cyclop.Cyclop.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KlineData {
    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("openPrice")
    private double openPrice;
    @JsonProperty("currentPrice")
    private double currentPrice;
    @JsonProperty("timestamp")
    private long timestamp;
    @JsonProperty("sourcePlatform")
    private String sourcePlatform;
    @JsonProperty("interval")
    private String interval;

    @JsonIgnore
    public String getKafkaKey() {
        return String.join(".",sourcePlatform,symbol,interval);
    }
}
