package com.tbot.cyclop.Cyclop.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MexcOrderHistoryResponse {
    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("positionId")
    private int positionId;

    @JsonProperty("price")
    private double price;

    @JsonProperty("vol")
    private int vol;

    @JsonProperty("leverage")
    private int leverage;

    @JsonProperty("side")
    private int side;

    @JsonProperty("category")
    private int category;

    @JsonProperty("orderType")
    private int orderType;

    @JsonProperty("dealAvgPrice")
    private double dealAvgPrice;

    @JsonProperty("dealVol")
    private int dealVol;

    @JsonProperty("orderMargin")
    private double orderMargin;

    @JsonProperty("takerFee")
    private double takerFee;

    @JsonProperty("makerFee")
    private double makerFee;

    @JsonProperty("profit")
    private double profit;

    @JsonProperty("feeCurrency")
    private String feeCurrency;

    @JsonProperty("openType")
    private int openType;

    @JsonProperty("state")
    private int state;

    @JsonProperty("externalOid")
    private String externalOid;

    @JsonProperty("errorCode")
    private int errorCode;

    @JsonProperty("usedMargin")
    private double usedMargin;

    @JsonProperty("createTime")
    private long createTime;

    @JsonProperty("updateTime")
    private long updateTime;
}

