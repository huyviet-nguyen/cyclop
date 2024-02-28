package com.tbot.cyclop.Cyclop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbot.cyclop.Cyclop.dto.TokenPairData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class BybitTokenPairData {
    private static final String SOURCE_PLATFORM = "BYBIT";
    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("lastPrice")
    private double lastPrice;
    @JsonProperty("indexPrice")
    private double indexPrice;

    public TokenPairData toDto() {
        return new TokenPairData(this.getSymbol(), this.getLastPrice(), this.getIndexPrice(), Instant.now().toEpochMilli(), SOURCE_PLATFORM);
    }
}
