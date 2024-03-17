package com.tbot.cyclop.Cyclop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelegramNotiPayload {
    @JsonProperty("chat_id")
    private String chatId;
    private String text;
    @JsonProperty("disable_notification")
    private boolean disableNotification;
    private String parseMode = "Markdown";
}
