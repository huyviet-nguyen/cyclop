package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "bot")
public class Bot {

    @Id
    private String id;
    private String name;
    private String platform;
    private String note;
    private String status;
    private String user;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Balance futuresBalance;
    private Balance spotBalance;
    private ApiInfo apiKey;
    private FingerprintSysInfo fingerprintSysInfo;
}

