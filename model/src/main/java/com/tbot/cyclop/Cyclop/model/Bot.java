package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Document(collection = "bot")
public class Bot implements Serializable {

    @Id
    private String id;
    private String name;
    private String platform;
    private String note;
    private String status;
    @DocumentReference
    private User user;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Balance futuresBalance;
    private Balance spotBalance;
    private String apiKey;
    private String secretKey;
    private String webToken;
    private String telegramId;
    private FingerprintSysInfo fingerprintSysInfo;
    private int winCount;
    private int loseCount;

    public void win() {
        winCount++;
    }

    public void lose() {
        loseCount++;
    }
}

