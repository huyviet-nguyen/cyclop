package com.tbot.cyclop.Cyclop.dto.req.bybit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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

    @JsonProperty("triggerDirection")
    private Integer triggerDirection;

    @JsonProperty("triggerPrice")
    private String triggerPrice;

    @JsonProperty("takeProfit")
    private String takeProfitPrice;

    @JsonProperty("stopLoss")
    private String stopLossPrice;

    @JsonProperty("tpslMode")
    private String tpslMode = "Full";

    @JsonProperty("orderLinkId")
    private String orderLinkId;
}

