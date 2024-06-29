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

    @JsonProperty("price")
    private String price;

    @JsonProperty("triggerPrice")
    private String triggerPrice;

    public void setPrice(String price) {
        this.price = price;
        this.triggerPrice = price;
    }
}
