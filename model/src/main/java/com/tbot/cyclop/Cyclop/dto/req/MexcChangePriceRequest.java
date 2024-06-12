package com.tbot.cyclop.Cyclop.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcChangePriceRequest {
    private String orderId;
    private String stopLossPrice;
    private String takeProfitPrice;
    private String profitTrend;
    private String lossTrend;
    private int takeProfitVolume;
    private int stopLossVolume;
    private int takeProfitReverse;
    private int stopLossReverse;
}
