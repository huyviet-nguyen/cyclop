package com.tbot.cyclop.orderplacer.exception;

import com.tbot.cyclop.Cyclop.model.Order;

public class SyncStatusFailException extends RuntimeException {
    public SyncStatusFailException(Order ackHistory) {
        super(String.format("FAILED TO SYNC ORDER STATUS FOR %s FROM %s",
                ackHistory.getSymbolWithUnderScore(), ackHistory.getPlatform()));
    }
}
