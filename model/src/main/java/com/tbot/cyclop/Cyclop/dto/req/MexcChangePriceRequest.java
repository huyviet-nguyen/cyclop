package com.tbot.cyclop.Cyclop.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcChangePriceRequest {
    private String orderId;
    private double stopLossPrice;
    private double takeProfitPrice;
    private String profitTrend;
    private String lossTrend;
    private int takeProfitVolume;
    private int stopLossVolume;
    private int takeProfitReverse;
    private int stopLossReverse;
}
