package com.tbot.cyclop.Cyclop.dto.req.bybit;

public enum BybitOrderStatus {
    FILLED("Filled"),
    CANCELLED("Cancelled"),
    TRIGGERED("Triggered");
    private final String status;

    BybitOrderStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return status;
    }
}
