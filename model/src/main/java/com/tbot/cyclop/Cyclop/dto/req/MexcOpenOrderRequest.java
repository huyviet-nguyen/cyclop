package com.tbot.cyclop.Cyclop.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MexcOpenOrderRequest {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("trend")
    private int trend = 1;

    @JsonProperty("triggerPrice")
    private String triggerPrice;

    @JsonProperty("side")
    private int side; // 1=open long | 3 = open short

    @JsonProperty("triggerType")
    private int triggerType; // 1=open long | 2 = open short

    @JsonProperty("openType")
    private int openType = 1; // Default value 1 for ISOLATED

    @JsonProperty("orderType")
    private int orderType = 5;

    @JsonProperty("positionMode")
    private int positionMode = 1;

    @JsonProperty("vol")
    private double vol;

    @JsonProperty("leverage")
    private int leverage;
    // Default value false for limit

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
    private String takeProfitPrice;

    @JsonProperty("stopLossPrice")
    private String stopLossPrice;

    @JsonProperty("profitTrend")
    private String profitTrend = "1";

    @JsonProperty("lossTrend")
    private String lossTrend = "1";

    @JsonProperty("executeCycle")
    private int executeCycle = 3;

}
