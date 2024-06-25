package com.tbot.cyclop.Cyclop.dto.req.bybit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BybitReduceTpReq {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("takeProfit")
    private String takeProfitPrice;
}
