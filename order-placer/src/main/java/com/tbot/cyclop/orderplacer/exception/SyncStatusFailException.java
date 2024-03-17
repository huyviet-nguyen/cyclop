package com.tbot.cyclop.orderplacer.exception;

import com.tbot.cyclop.Cyclop.model.OrderAckHistory;

public class SyncStatusFailException extends RuntimeException {
    public SyncStatusFailException(OrderAckHistory ackHistory) {
        super(String.format("FAILED TO SYNC ORDER STATUS FOR %s FROM %s",
                ackHistory.getSymbolWithUnderScore(), ackHistory.getPlatform()));
    }
}
