package com.tbot.cyclop.Cyclop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Candle {
    private CandleInterval candleStick;
    private String symbol;
    private String openPrice;
    private String closePrice;
}
