package com.tbot.cyclop.Cyclop.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MexcOpenOrderRequest {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("price")
    private double price;

    @JsonProperty("side")
    private String side; // 1=open long | 3 = open short

    @JsonProperty("openType")
    private int openType = 1; // Default value 1 for ISOLATED

    @JsonProperty("type")
    private String type = "1";

    @JsonProperty("vol")
    private double vol;

    @JsonProperty("leverage")
    private int leverage;

    @JsonProperty("marketCeiling")
    private boolean marketCeiling = false; // Default value false for limit

    @JsonProperty("priceProtect")
    private String priceProtect = "0"; // Default value "0" for priceProtect

    @JsonProperty("p0")
    private String p0;

    @JsonProperty("k0")
    private String k0;

    @JsonProperty("chash")
    private String cHash;

    @JsonProperty("mtoken")
    private String mToken;

    @JsonProperty("ts")
    private long timestamp;

    @JsonProperty("mhash")
    private String mHash;

    @JsonProperty("takeProfitPrice")
    private double takeProfitPrice;

    @JsonProperty("stopLossPrice")
    private double stopLossPrice;

}
