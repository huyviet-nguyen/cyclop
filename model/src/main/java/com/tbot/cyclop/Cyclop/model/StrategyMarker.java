package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "reduce_tp")
public class StrategyMarker {
    @Id
    private String id;
    private double actualTp;
}
