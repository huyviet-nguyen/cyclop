package com.tbot.cyclop.Cyclop.dto.res.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitKline {
    @JsonProperty("topic")
    private String topic;

    @JsonProperty("data")
    private List<BybitKlineDetail> data;

    @JsonProperty("ts")
    private long timestamp;

    @JsonProperty("type")
    private String type;

    public String getSymbol() {
        return this.getTopic().split("\\.")[2];
    }

    public KlineData toDto() {
        KlineData pairData = new KlineData();
        pairData.setCandleTimestamp(timestamp);
        pairData.setSymbol(getSymbol());
        pairData.setOpenPrice(Double.parseDouble(this.getData().getFirst().getOpen()));
        pairData.setCurrentPrice(Double.parseDouble(this.getData().getFirst().getClose()));
        pairData.setInterval(this.getData().getFirst().getInterval());
        pairData.setSourcePlatform("BYBIT");
        return pairData;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BybitKlineDetail {

        @JsonProperty("interval")
        private String interval;

        @JsonProperty("open")
        private String open;

        @JsonProperty("close")
        private String close;

        @JsonProperty("timestamp")
        private long timestamp;
    }
}
