package com.tbot.cyclop.Cyclop.dto.res.mexc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcOrderHistoryListResponse {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("code")
    private int code;

    @JsonProperty("data")
    private List<MexcOrderHistoryResponse> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MexcOrderHistoryResponse {
        @JsonProperty("orderId")
        private String orderId;

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("positionId")
        private long positionId;

        @JsonProperty("price")
        private double price;

        @JsonProperty("priceStr")
        private String priceStr;

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

        @JsonProperty("dealAvgPriceStr")
        private String dealAvgPriceStr;

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

        @JsonProperty("positionMode")
        private int positionMode;

        @JsonProperty("version")
        private int version;

        @JsonProperty("showCancelReason")
        private int showCancelReason;

        @JsonProperty("showProfitRateShare")
        private int showProfitRateShare;

        @JsonProperty("stopLossPrice")
        private double stopLossPrice;

        @JsonProperty("takeProfitPrice")
        private double takeProfitPrice;

        // Additional fields can be added as needed
    }
}
