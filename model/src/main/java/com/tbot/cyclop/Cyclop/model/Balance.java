package com.tbot.cyclop.Cyclop.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class Balance implements Serializable {
    private double total;
    private double free;
    private double saving;  // Assuming this is a saving balance field
}

