package com.tbot.cyclop.Cyclop.dto.res.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitGetOrderListResponse {

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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        @JsonProperty("list")
        private List<BybitGetOrderResponse> list;

        @JsonProperty("nextPageCursor")
        private String nextPageCursor;

        @JsonProperty("category")
        private String category;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RetExtInfo {
    }
}
