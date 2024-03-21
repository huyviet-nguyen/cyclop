package com.tbot.cyclop.Cyclop.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TelegramNotiPayload {
    @JsonProperty("chat_id")
    private String chatId;
    private String text;
    @JsonProperty("disable_notification")
    private boolean disableNotification;
    @JsonProperty("parse_mode")
    private String parseMode = "markdown";
}
