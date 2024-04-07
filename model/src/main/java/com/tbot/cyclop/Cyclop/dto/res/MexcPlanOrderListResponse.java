package com.tbot.cyclop.Cyclop.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcPlanOrderListResponse {
    private boolean success;
    private int code;
    private List<MexcPlanOrderResponse> data;
}

