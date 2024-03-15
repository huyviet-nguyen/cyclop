package com.tbot.cyclop.orderplacer.exception;

import com.tbot.cyclop.Cyclop.model.OrderAckHistory;

public class OpenOrderFailException extends RuntimeException {

    public OpenOrderFailException(OrderAckHistory ackHistory, Throwable e) {
        super(String.format("FAILED TO PLACE ORDER FOR %s AT PRICE %s, EXCEPTION HAPPENED : %s",
                ackHistory.getSymbolWithUnderScore(), ackHistory.getEntryPrice(), e.getMessage()), e);
    }

    public OpenOrderFailException(OrderAckHistory ackHistory) {
        super(String.format("FAILED TO PLACE ORDER FOR %s AT PRICE %s, EXCEPTION HAPPENED",
                ackHistory.getSymbolWithUnderScore(), ackHistory.getEntryPrice()));
    }
}
