package com.tbot.cyclop.Cyclop.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcOrderResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("code")
    private int code;

    @JsonProperty("data")
    private MexcOrderResponseInner data;


    @Getter
    @Setter
    public static class MexcOrderResponseInner {
        @JsonProperty("orderId")
        private String orderId;
        @JsonProperty("ts")
        private long ts;
    }

}

