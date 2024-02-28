package com.tbot.cyclop.Cyclop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbot.cyclop.Cyclop.dto.TokenPairData;


@JsonIgnoreProperties(ignoreUnknown = true)
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

    public MexcTokenPairData(String symbol, double lastPrice, double indexPrice, long timestamp) {
        this.symbol = symbol;
        this.lastPrice = lastPrice;
        this.indexPrice = indexPrice;
        this.timestamp = timestamp;
    }

    public MexcTokenPairData() {
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(double lastPrice) {
        this.lastPrice = lastPrice;
    }

    public double getIndexPrice() {
        return indexPrice;
    }

    public void setIndexPrice(double indexPrice) {
        this.indexPrice = indexPrice;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public TokenPairData toDto() {
        return new TokenPairData(this.getSymbol().replace("_", ""), this.getLastPrice(), this.getIndexPrice(), this.getTimestamp(), SOURCE_PLATFORM);
    }
}
