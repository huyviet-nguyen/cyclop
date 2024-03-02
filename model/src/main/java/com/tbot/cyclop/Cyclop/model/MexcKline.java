package com.tbot.cyclop.Cyclop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import lombok.Data;

import java.util.Objects;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcKline {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("data")
    private MexcKlineDetail mexcKlineDetail;

    @JsonProperty("ts")
    private long timestamp;

    @JsonIgnore
    public String getNormalizedSymbol() {
        return Objects.requireNonNull(symbol).replace("_", "");
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MexcKlineDetail {
        @JsonProperty("interval")
        private String interval;

        @JsonProperty("t")
        private long t;

        @JsonProperty("o")
        private double o;

        @JsonProperty("c")
        private double c;

        public String getNormalizedInterval() {
            return this.interval.replace("MIN", "").replace("Min", "");
        }
    }

    public KlineData toDto() {
        KlineData klineData = new KlineData();
        klineData.setSourcePlatform("MEXC");
        klineData.setOpenPrice(this.getMexcKlineDetail().getO());
        klineData.setCurrentPrice(this.getMexcKlineDetail().getC());
        klineData.setTimestamp(this.getTimestamp());
        klineData.setInterval(this.getMexcKlineDetail().getNormalizedInterval());
        klineData.setSymbol(getNormalizedSymbol());
        return klineData;
    }
}
