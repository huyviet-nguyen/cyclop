package com.tbot.cyclop.Cyclop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MexcMarketData {
    @JsonProperty("data")
    private Set<MexcTokenPairData> data;
    @JsonProperty("ts")
    private String ts;
}
