package com.tbot.cyclop.Cyclop.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcStopOrderResponse {
    private String id;
    private String orderId;
    private String positionId;
    private String profitTrend;
    private String lossTrend;
    private int takeProfitVol;
    private int stopLossVol;
    private int takeProfitReverse;
    private int stopLossReverse;
}
