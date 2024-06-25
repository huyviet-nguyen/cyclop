package com.tbot.cyclop.Cyclop.dto.res.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitOrderRes {

    @JsonProperty("retCode")
    private int retCode;

    @JsonProperty("retMsg")
    private String retMsg;

    @JsonProperty("result")
    private Result result;

    @JsonProperty("retExtInfo")
    private RetExtInfo retExtInfo;

    @JsonProperty("time")
    private long time;

    @Data
    public static class Result {
        @JsonProperty("orderId")
        private String orderId;

        @JsonProperty("orderLinkId")
        private String orderLinkId;
    }

    @Data
    public static class RetExtInfo {
    }
}
