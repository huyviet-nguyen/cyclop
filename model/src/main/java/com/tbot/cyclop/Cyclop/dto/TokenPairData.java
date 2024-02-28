package com.tbot.cyclop.Cyclop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenPairData {
    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("lastPrice")
    private double lastPrice;
    @JsonProperty("indexPrice")
    private double indexPrice;
    @JsonProperty("timestamp")
    private long timestamp;
    @JsonProperty("sourcePlatform")
    private String sourcePlatform;
}
