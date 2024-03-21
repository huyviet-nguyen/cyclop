package com.tbot.cyclop.orderplacer.exception;

import com.tbot.cyclop.Cyclop.model.Order;

public class OpenOrderFailException extends RuntimeException {

    public OpenOrderFailException(Order ackHistory, Throwable e) {
        super(String.format("FAILED TO PLACE ORDER FOR %s AT PRICE %s, EXCEPTION HAPPENED : %s",
                ackHistory.getSymbol(), ackHistory.getEntryPrice(), e.getMessage()), e);
    }

    public OpenOrderFailException(Order ackHistory) {
        super(String.format("FAILED TO PLACE ORDER FOR %s AT PRICE %s, EXCEPTION HAPPENED",
                ackHistory.getSymbol(), ackHistory.getEntryPrice()));
    }
}
