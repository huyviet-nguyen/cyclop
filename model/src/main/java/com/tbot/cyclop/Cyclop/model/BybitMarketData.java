package com.tbot.cyclop.Cyclop.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class BybitMarketData {
    @JsonProperty("data")
    private BybitTokenPairData data;
    @JsonProperty("ts")
    private String ts;
}
