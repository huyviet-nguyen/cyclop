package com.tbot.cyclop.Cyclop.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class MexcOrderResponse {

    @JsonProperty("ret_code")
    private int retCode;

    @JsonProperty("ret_msg")
    private String retMsg;

    @JsonProperty("ext_code")
    private String extCode;

    @JsonProperty("ext_info")
    private String extInfo;

    @JsonProperty("result")
    private Result result;

    @JsonProperty("time_now")
    private String timeNow;

    @JsonProperty("rate_limit_status")
    private int rateLimitStatus;

    @Data
    public static class Result {
        @JsonProperty("order_id")
        private String orderId;

        @JsonProperty("user_id")
        private int userId;

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("side")
        private String side;

        @JsonProperty("order_type")
        private String orderType;

        @JsonProperty("price")
        private String price;

        @JsonProperty("qty")
        private String quantity;

        @JsonProperty("time_in_force")
        private String timeInForce;

        @JsonProperty("order_status")
        private String orderStatus;

        @JsonProperty("ext_fields")
        private ExtFields extFields;

        @JsonProperty("time")
        private long time;

        @JsonProperty("trade_list")
        private List<Trade> tradeList;
    }

    @Data
    public static class ExtFields {
        @JsonProperty("cross_seq")
        private long crossSeq;

        @JsonProperty("stop_order_id")
        private String stopOrderId;

        @JsonProperty("cross_order_id")
        private String crossOrderId;

        @JsonProperty("order_id")
        private String originalOrderId;

        @JsonProperty("api_key")
        private String apiKey;

        @JsonProperty("timestamp")
        private long timestamp;

        @JsonProperty("recv_window")
        private int recvWindow;

        @JsonProperty("signature")
        private String signature;
    }

    @Data
    public static class Trade {
        // Define trade properties here if available in the response
    }
}

