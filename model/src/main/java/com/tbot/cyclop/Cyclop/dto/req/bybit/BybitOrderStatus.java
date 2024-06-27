package com.tbot.cyclop.Cyclop.dto.req.bybit;

public enum BybitOrderStatus {
    Created("Created"),
    New("New"),
    Rejected("Rejected"),
    PartiallyFilled("PartiallyFilled"),
    Filled("Filled"),
    PendingCancel("PendingCancel"),
    Cancelled("Cancelled"),
    Untriggered("Untriggered"),
    Triggered("Triggered"),
    Deactivated("Deactivated"),
    Active("Active");

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
