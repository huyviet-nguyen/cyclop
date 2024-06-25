package com.tbot.cyclop.Cyclop.dto.req.bybit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BybitCancelOrderReq {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("orderLinkId")
    private String orderLinkId;

    @JsonProperty("orderId")
    private String orderId;
}
