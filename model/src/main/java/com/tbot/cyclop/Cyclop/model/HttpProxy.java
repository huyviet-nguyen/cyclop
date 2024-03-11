package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "http_proxy")
@Data
public class HttpProxy {
    @Id
    private String id;
    private String proxyUrl;
    private int proxyPort;
    private String proxyUsername;
    private String password;
}
