package com.tbot.cyclop.orderplacer.exception;

import com.tbot.cyclop.Cyclop.model.OrderAckHistory;

public class ReduceTakeProfitFailException extends RuntimeException {
    public ReduceTakeProfitFailException(OrderAckHistory ackHistory, Throwable e) {
        super(String.format("FAILED TO REDUCE TAKE PROFIT FOR ORDER %s, EXCEPTION HAPPENED : %s",
                ackHistory.getPlatformOrderId(), e.getMessage()), e);
    }
    public ReduceTakeProfitFailException(OrderAckHistory ackHistory) {
        super(String.format("FAILED TO REDUCE TAKE PROFIT FOR ORDER %s, EXCEPTION HAPPENED",
                ackHistory.getPlatformOrderId()));
    }
}
