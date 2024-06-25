package com.tbot.cyclop.Cyclop.dto.res.mexc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcChangeOrderResponse {
    private boolean success;
}
