package com.tbot.cyclop.Cyclop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbot.cyclop.Cyclop.dto.TokenPairData;

import java.time.Instant;
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitTokenPairData {
    private static final String SOURCE_PLATFORM = "BYBIT";
    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("lastPrice")
    private double lastPrice;
    @JsonProperty("indexPrice")
    private double indexPrice;

    public BybitTokenPairData(String symbol, double lastPrice, double indexPrice) {
        this.symbol = symbol;
        this.lastPrice = lastPrice;
        this.indexPrice = indexPrice;
    }

    public BybitTokenPairData() {
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

    public TokenPairData toDto() {
        return new TokenPairData(this.getSymbol(), this.getLastPrice(), this.getIndexPrice(), Instant.now().toEpochMilli(), SOURCE_PLATFORM);
    }
}
