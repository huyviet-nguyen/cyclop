package com.tbot.cyclop.Cyclop.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MexcPositionResponse {
    @JsonProperty("positionId")
    private long positionId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("positionType")
    private int positionType;

    @JsonProperty("openType")
    private int openType;

    @JsonProperty("state")
    private int state;

    @JsonProperty("holdVol")
    private int holdVol;

    @JsonProperty("frozenVol")
    private int frozenVol;

    @JsonProperty("closeVol")
    private int closeVol;

    @JsonProperty("holdAvgPrice")
    private double holdAvgPrice;

    @JsonProperty("holdAvgPriceFullyScale")
    private String holdAvgPriceFullyScale;

    @JsonProperty("openAvgPrice")
    private double openAvgPrice;

    @JsonProperty("openAvgPriceFullyScale")
    private String openAvgPriceFullyScale;

    @JsonProperty("closeAvgPrice")
    private double closeAvgPrice;

    @JsonProperty("liquidatePrice")
    private double liquidatePrice;

    @JsonProperty("oim")
    private int oim;

    @JsonProperty("im")
    private int im;

    @JsonProperty("holdFee")
    private double holdFee;

    @JsonProperty("realised")
    private double realised;

    @JsonProperty("leverage")
    private int leverage;

    @JsonProperty("createTime")
    private long createTime;

    @JsonProperty("updateTime")
    private long updateTime;

    @JsonProperty("autoAddIm")
    private boolean autoAddIm;

    @JsonProperty("version")
    private int version;

    @JsonProperty("profitRatio")
    private double profitRatio;

    @JsonProperty("newOpenAvgPrice")
    private double newOpenAvgPrice;

    @JsonProperty("newCloseAvgPrice")
    private double newCloseAvgPrice;

    @JsonProperty("closeProfitLoss")
    private double closeProfitLoss;

    @JsonProperty("fee")
    private double fee;

    @JsonProperty("positionShowStatus")
    private String positionShowStatus;
}

