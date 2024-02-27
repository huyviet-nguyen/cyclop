package com.tbot.cyclop.Cyclop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcMarketData {
    @JsonProperty("data")
    private Set<MexcTokenPairData> data;
    @JsonProperty("ts")
    private String ts;

    public MexcMarketData(Set<MexcTokenPairData> data, String ts) {
        this.data = data;
        this.ts = ts;
    }

    public MexcMarketData() {
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public Set<MexcTokenPairData> getData() {
        return data;
    }

    public void setData(Set<MexcTokenPairData> data) {
        this.data = data;
    }

}
