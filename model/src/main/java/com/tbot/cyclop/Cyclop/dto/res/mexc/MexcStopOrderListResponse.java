package com.tbot.cyclop.Cyclop.dto.res.mexc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcStopOrderListResponse {
    private List<MexcStopOrderResponse> data;
}
