package com.tbot.cyclop.Cyclop.dto.res.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbot.cyclop.Cyclop.dto.req.bybit.BybitOrderStatus;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitGetOrderResponse {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private String side;

    @JsonProperty("orderType")
    private String orderType;

    @JsonProperty("price")
    private String price;

    @JsonProperty("qty")
    private String qty;

    @JsonProperty("reduceOnly")
    private boolean reduceOnly;

    @JsonProperty("timeInForce")
    private String timeInForce;

    @JsonProperty("orderStatus")
    private BybitOrderStatus orderStatus;

    @JsonProperty("leavesQty")
    private String leavesQty;

    @JsonProperty("leavesValue")
    private String leavesValue;

    @JsonProperty("cumExecQty")
    private String cumExecQty;

    @JsonProperty("cumExecValue")
    private String cumExecValue;

    @JsonProperty("cumExecFee")
    private String cumExecFee;

    @JsonProperty("lastPriceOnCreated")
    private String lastPriceOnCreated;

    @JsonProperty("rejectReason")
    private String rejectReason;

    @JsonProperty("orderLinkId")
    private String orderLinkId;

    @JsonProperty("createdTime")
    private long createdTime;

    @JsonProperty("updatedTime")
    private long updatedTime;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("stopOrderType")
    private String stopOrderType;

    @JsonProperty("takeProfit")
    private String takeProfit;

    @JsonProperty("stopLoss")
    private String stopLoss;

    @JsonProperty("tpTriggerBy")
    private String tpTriggerBy;

    @JsonProperty("slTriggerBy")
    private String slTriggerBy;

    @JsonProperty("triggerPrice")
    private String triggerPrice;

    @JsonProperty("closeOnTrigger")
    private boolean closeOnTrigger;

    @JsonProperty("triggerDirection")
    private int triggerDirection;

    @JsonProperty("positionIdx")
    private int positionIdx;

    @JsonProperty("cancelType")
    private String cancelType;

    @JsonProperty("iv")
    private String iv;

    @JsonProperty("triggerBy")
    private String triggerBy;

    @JsonProperty("blockTradeId")
    private String blockTradeId;

    @JsonProperty("tpslMode")
    private String tpslMode;

    @JsonProperty("tpLimitPrice")
    private String tpLimitPrice;

    @JsonProperty("slLimitPrice")
    private String slLimitPrice;

    @JsonProperty("smpType")
    private String smpType;

    @JsonProperty("smpGroup")
    private int smpGroup;

    @JsonProperty("smpOrderId")
    private String smpOrderId;
}
