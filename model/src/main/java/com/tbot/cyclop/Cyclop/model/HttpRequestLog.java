package com.tbot.cyclop.Cyclop.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "req_logs")
@Getter
@Setter
public class HttpRequestLog {
    @Id
    private String id;
    private String url;
    private String requestBody;
    private String responseBody;
    private LocalDateTime requestTime;
    private LocalDateTime responseTime;
    private String symbolString;
    private String strategyId;
}
