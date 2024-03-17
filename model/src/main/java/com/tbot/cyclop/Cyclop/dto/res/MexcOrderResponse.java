package com.tbot.cyclop.Cyclop.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MexcOrderResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("code")
    private int code;

    @JsonProperty("data")
    private Order data;

    @Data
    public static class Order {
        @JsonProperty("orderId")
        private String orderId;

        @JsonProperty("ts")
        private long ts;
    }
}

