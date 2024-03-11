package com.tbot.cyclop.Cyclop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OrderPlacePayload {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private int side;

    @JsonProperty("openType")
    private int openType;

    @JsonProperty("type")
    private String type;

    @JsonProperty("vol")
    private int vol;

    @JsonProperty("leverage")
    private int leverage;

    @JsonProperty("marketCeiling")
    private boolean marketCeiling;

    @JsonProperty("stopLossPrice")
    private String stopLossPrice;

    @JsonProperty("takeProfitPrice")
    private String takeProfitPrice;

    @JsonProperty("lossTrend")
    private String lossTrend;

    @JsonProperty("profitTrend")
    private String profitTrend;

    @JsonProperty("priceProtect")
    private String priceProtect;

    @JsonProperty("p0")
    private String p0;

    @JsonProperty("k0")
    private String k0;

    @JsonProperty("chash")
    private String chash;

    @JsonProperty("mtoken")
    private String mtoken;

    @JsonProperty("ts")
    private long ts;

    @JsonProperty("mhash")
    private String mhash;

}
