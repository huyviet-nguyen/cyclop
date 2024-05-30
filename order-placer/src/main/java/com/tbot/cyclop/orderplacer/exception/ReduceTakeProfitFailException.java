package com.tbot.cyclop.orderplacer.exception;

import com.tbot.cyclop.Cyclop.model.Order;

public class ReduceTakeProfitFailException extends RuntimeException {
    public ReduceTakeProfitFailException(Order ackHistory, Throwable e) {
        super(String.format("FAILED TO REDUCE TAKE PROFIT FOR ORDER %s, EXCEPTION HAPPENED : %s",
                ackHistory.getPlatformOrderId(), e.getMessage()), e);
    }

    public ReduceTakeProfitFailException(Order ackHistory) {
        super(String.format("FAILED TO REDUCE TAKE PROFIT FOR ORDER %s, EXCEPTION HAPPENED",
                ackHistory.getPlatformOrderId()));
    }

    public ReduceTakeProfitFailException(Order ackHistory, String message) {
        super(String.format("FAILED TO REDUCE TAKE PROFIT FOR ORDER %s, LOOKING FOR %s, FOUND %s",
                ackHistory.getPlatformOrderId(), ackHistory.getPlatformOrderId(), message));
    }
}
