package com.tbot.cyclop.Cyclop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import lombok.Data;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MexcKline {
    @JsonProperty("d")
    private KlineDetail klineDetail;

    @JsonProperty("t")
    private long timestamp;

    @JsonProperty("s")
    private String symbol;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KlineDetail {

        @JsonProperty("k")
        private KlineDataItem klineDataItem;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KlineDataItem {

        @JsonProperty("o")
        private String open;

        @JsonProperty("c")
        private String close;

        @JsonProperty("i")
        private String interval;

        @JsonIgnore
        public String getNormalizedInterval(){
            return interval.replace("Min","");
        }
    }

    public KlineData toDto() {
        KlineData pairData = new KlineData();
        pairData.setTimestamp(timestamp);
        pairData.setSymbol(getSymbol());
        pairData.setOpenPrice(Double.parseDouble(getKlineDetail().getKlineDataItem().getOpen()));
        pairData.setCurrentPrice(Double.parseDouble(getKlineDetail().getKlineDataItem().getClose()));
        pairData.setInterval(getKlineDetail().getKlineDataItem().getNormalizedInterval());
        pairData.setSourcePlatform("MEXC");
        return pairData;
    }
}