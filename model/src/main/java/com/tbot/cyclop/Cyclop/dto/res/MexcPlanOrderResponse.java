package com.tbot.cyclop.Cyclop.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcPlanOrderResponse {
    private String id;
    private String symbol;
    private int leverage;
    private int side;
    private double triggerPrice;
    private int vol;
    private int openType;
    private int triggerType;
    private int state;
    private int executeCycle;
    private int trend;
    private int orderType;
    private int errorCode;
    private int priceProtect;
    private long createTime;
    private long updateTime;
    private int positionMode;
    private int lossTrend;
    private int profitTrend;
    private double stopLossPrice;
    private double takeProfitPrice;
    private boolean reduceOnly;
}

