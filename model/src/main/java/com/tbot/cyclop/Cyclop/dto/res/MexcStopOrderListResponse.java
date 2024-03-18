package com.tbot.cyclop.Cyclop.dto.res;

import lombok.Data;

import java.util.List;

@Data
public class MexcStopOrderListResponse {
    private List<MexcStopOrderResponse> data;
}
