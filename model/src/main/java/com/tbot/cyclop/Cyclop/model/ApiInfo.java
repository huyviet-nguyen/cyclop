package com.tbot.cyclop.Cyclop.model;

import lombok.Data;

@Data
public class ApiInfo {
    private String apiKey;
    private String secretKey;
    private String webToken;
    private String telegramId;
}
