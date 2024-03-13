package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "telegram_bot_info")
@Data
public class TelegramBotInfo {
    @Id
    private String id;
    private String apiToken;
}
