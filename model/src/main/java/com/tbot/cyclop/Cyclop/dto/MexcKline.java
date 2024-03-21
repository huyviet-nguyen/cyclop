package com.tbot.cyclop.Cyclop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = false)
public class MexcKline {
    private String symbol;
    private DetailData data;
    private String channel;
    private long ts;

    @Getter
    @Setter
    public static class DetailData {
        private String symbol;
        private String interval;
        private long t;
        private double o;
        private double c;
        private double h;
        private double l;
        private double a;
        private int q;
        @JsonProperty("ro")
        private double open;
        @JsonProperty("rc")
        private double close;
        @JsonProperty("rh")
        private double high;
        @JsonProperty("rl")
        private double low;

        public String getNumInterval(){
            return this.interval.replace("Min","");
        }
    }


    public KlineData toDto() {
        KlineData klineData = new KlineData();
        klineData.setSourcePlatform("MEXC");
        klineData.setOpenPrice(this.getData().getO());
        klineData.setCurrentPrice(this.getData().getC());
        klineData.setTimestamp(this.getTs());
        klineData.setInterval(this.getData().getNumInterval());
        klineData.setSymbol(getSymbol().replace("_",""));
        return klineData;
    }
}
