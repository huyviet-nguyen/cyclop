package com.tbot.cyclop.Cyclop.dto.req.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BybitOrderReq {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private String side;

    @JsonProperty("orderType")
    private String orderType = "Limit";

    @JsonProperty("qty")
    private String quantity;

    @JsonProperty("price")
    private String price;

    @JsonProperty("timeInForce")
    private String timeInForce= "GoodTillCancel";

    @JsonProperty("positionIdx")
    private String positionIdx;


    @JsonProperty("takeProfit")
    private String takeProfitPrice;

    @JsonProperty("tpLimitPrice")
    private String tpLimitPrice;

    @JsonProperty("stopLoss")
    private String stopLossPrice;

    @JsonProperty("slLimitPrice")
    private String slLimitPrice;

    @JsonProperty("tpslMode")
    private String tpslMode = "Partial";

    @JsonProperty("orderLinkId")
    private String orderLinkId;

    @JsonProperty("tpOrderType")
    private String tpOrderType = "Limit";

    @JsonProperty("slOrderType")
    private String slOrderType = "Limit";


    public void setTakeProfitPrice(String takeProfitPrice) {
        this.takeProfitPrice = takeProfitPrice;
        this.tpLimitPrice = takeProfitPrice;
    }

    public void setStopLossPrice(String stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
        this.slLimitPrice = stopLossPrice;
    }
}

