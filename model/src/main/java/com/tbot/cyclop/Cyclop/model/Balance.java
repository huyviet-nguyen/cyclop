package com.tbot.cyclop.Cyclop.model;

import lombok.Data;

@Data
public class Balance {
    private double total;
    private double free;
    private double saving;  // Assuming this is a saving balance field
}

