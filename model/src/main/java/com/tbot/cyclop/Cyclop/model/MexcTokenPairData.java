package com.tbot.cyclop.Cyclop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbot.cyclop.Cyclop.dto.TokenPairData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Data
@NoArgsConstructor
public class MexcTokenPairData {
    private static final String SOURCE_PLATFORM = "MEXC";
    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("lastPrice")
    private double lastPrice;
    @JsonProperty("indexPrice")
    private double indexPrice;
    @JsonProperty("timestamp")
    private long timestamp;

    public TokenPairData toDto() {
        return new TokenPairData(this.getSymbol().replace("_", ""), this.getLastPrice(), this.getIndexPrice(), this.getTimestamp(), SOURCE_PLATFORM);
    }
}
