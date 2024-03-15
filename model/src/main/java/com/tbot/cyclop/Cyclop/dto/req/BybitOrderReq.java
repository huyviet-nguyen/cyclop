package com.tbot.cyclop.Cyclop.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BybitOrderReq {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private String side;

    @JsonProperty("orderType")
    private String orderType;

    @JsonProperty("qty")
    private String quantity;

    @JsonProperty("price")
    private String price;

    @JsonProperty("timeInForce")
    private String timeInForce;

    @JsonProperty("positionIdx")
    private String positionIdx;

    @JsonProperty("triggerDirection")
    private Integer triggerDirection;

    @JsonProperty("triggerPrice")
    private String triggerPrice;

    @JsonProperty("takeProfitPrice")
    private String takeProfitPrice;

    @JsonProperty("stopLossPrice")
    private String stopLossPrice;
}

