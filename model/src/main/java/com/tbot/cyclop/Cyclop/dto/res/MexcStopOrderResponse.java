package com.tbot.cyclop.Cyclop.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcStopOrderResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("positionId")
    private String positionId;

    @JsonProperty("lossTrend")
    private String lossTrend;

    @JsonProperty("profitTrend")
    private String profitTrend;

    @JsonProperty("stopLossPrice")
    private double stopLossPrice;

    @JsonProperty("takeProfitPrice")
    private double takeProfitPrice;

    @JsonProperty("state")
    private int state;

    @JsonProperty("triggerSide")
    private int triggerSide;

    @JsonProperty("positionType")
    private int positionType;

    @JsonProperty("vol")
    private int vol;

    @JsonProperty("realityVol")
    private int realityVol;

    @JsonProperty("placeOrderId")
    private String placeOrderId;

    @JsonProperty("errorCode")
    private int errorCode;

    @JsonProperty("version")
    private int version;

    @JsonProperty("isFinished")
    private int isFinished;

    @JsonProperty("priceProtect")
    private int priceProtect;

    @JsonProperty("profitLossVolType")
    private String profitLossVolType;

    @JsonProperty("takeProfitVol")
    private int takeProfitVol;

    @JsonProperty("stopLossVol")
    private int stopLossVol;

    @JsonProperty("createTime")
    private long createTime;

    @JsonProperty("updateTime")
    private long updateTime;

    @JsonProperty("volType")
    private int volType;

    @JsonProperty("takeProfitReverse")
    private int takeProfitReverse;

    @JsonProperty("stopLossReverse")
    private int stopLossReverse;

    @JsonProperty("closeTryTimes")
    private int closeTryTimes;

    @JsonProperty("reverseTryTimes")
    private int reverseTryTimes;

    @JsonProperty("reverseErrorCode")
    private int reverseErrorCode;

    @JsonProperty("profit_LOSS_VOL_TYPE_SAME")
    private String profitLossVolTypeSame;

    @JsonProperty("profit_LOSS_VOL_TYPE_DIFFERENT")
    private String profitLossVolTypeDifferent;
}
