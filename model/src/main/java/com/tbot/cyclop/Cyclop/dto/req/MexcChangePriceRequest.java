package com.tbot.cyclop.Cyclop.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcChangePriceRequest {
    private String orderId;
    private double stopLossPrice;
    private double takeProfitPrice;
    private String profitTrend = "1";
    private String lossTrend = "1";
}
