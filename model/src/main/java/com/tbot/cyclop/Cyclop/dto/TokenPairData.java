package com.tbot.cyclop.Cyclop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    public TokenPairData(String symbol, double lastPrice, double indexPrice, long timestamp, String sourcePlatform) {
        this.symbol = symbol;
        this.lastPrice = lastPrice;
        this.indexPrice = indexPrice;
        this.timestamp = timestamp;
        this.sourcePlatform = sourcePlatform;
    }

    public TokenPairData() {
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

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }
}
