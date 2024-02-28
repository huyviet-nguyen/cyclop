package com.tbot.cyclop.Cyclop.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitMarketData {
    @JsonProperty("data")
    private BybitTokenPairData data;
    @JsonProperty("ts")
    private String ts;

    public BybitMarketData(BybitTokenPairData data, String ts) {
        this.data = data;
        this.ts = ts;
    }

    public BybitMarketData() {
    }

    public BybitTokenPairData getData() {
        return data;
    }

    public void setData(BybitTokenPairData data) {
        this.data = data;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }
}
