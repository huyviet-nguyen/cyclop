package com.tbot.cyclop.Cyclop.dto.req;

import lombok.Data;

@Data
public class MexcChangePriceRequest {
    private String orderId;
    private double stopLossPrice;
    private double takeProfitPrice;
    private long ts;
}
