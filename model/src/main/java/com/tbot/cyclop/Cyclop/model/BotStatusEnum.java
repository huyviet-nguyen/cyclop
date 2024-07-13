package com.tbot.cyclop.Cyclop.model;

public enum BotStatusEnum {
    RUNNING("RUNNING"),
    PENDING("PENDING"),
    MISSING_JWT("MISSING_JWT"),
    ERROR_JWT("ERROR_JWT"),
    STOPPED("STOPPED");

    BotStatusEnum(String status) {
    }
}
