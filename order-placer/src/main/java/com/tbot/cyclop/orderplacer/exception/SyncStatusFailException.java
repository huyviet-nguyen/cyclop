package com.tbot.cyclop.orderplacer.exception;

import com.tbot.cyclop.Cyclop.model.Order;

public class SyncStatusFailException extends RuntimeException {
    public SyncStatusFailException(Order ackHistory) {
        super(String.format("FAILED TO SYNC ORDER STATUS FOR %s | %s FROM %s", ackHistory.getPlatformOrderId(),
                ackHistory.getSymbol(), ackHistory.getPlatform()));
    }
}
