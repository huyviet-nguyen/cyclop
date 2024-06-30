package com.tbot.cyclop.Cyclop.dto.res.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitClosedPnlListResponse {

    @JsonProperty("retCode")
    private int retCode;

    @JsonProperty("retMsg")
    private String retMsg;

    @JsonProperty("result")
    private Result result;

    @JsonProperty("time")
    private long time;

    @Data
    public static class Result {
        @JsonProperty("list")
        private List<BybitClosedPnlResponse> list;

        @JsonProperty("nextPageCursor")
        private String nextPageCursor;
    }
}
