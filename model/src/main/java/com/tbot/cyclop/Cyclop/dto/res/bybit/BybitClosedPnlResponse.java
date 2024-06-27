package com.tbot.cyclop.Cyclop.dto.res.bybit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BybitClosedPnlResponse {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("side")
    private String side;

    @JsonProperty("qty")
    private String qty;

    @JsonProperty("orderPrice")
    private String orderPrice;

    @JsonProperty("orderType")
    private String orderType;

    @JsonProperty("execType")
    private String execType;

    @JsonProperty("closedSize")
    private String closedSize;

    @JsonProperty("cumEntryValue")
    private String cumEntryValue;

    @JsonProperty("avgEntryPrice")
    private String avgEntryPrice;

    @JsonProperty("cumExitValue")
    private String cumExitValue;

    @JsonProperty("avgExitPrice")
    private String avgExitPrice;

    @JsonProperty("closedPnl")
    private String closedPnl;

    @JsonProperty("fillCount")
    private String fillCount;

    @JsonProperty("leverage")
    private String leverage;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("createdTime")
    private String createdTime;

    @JsonProperty("updatedTime")
    private String updatedTime;
}
