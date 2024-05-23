package com.tbot.cyclop.Cyclop.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "error_trace")
@Getter
@Setter
public class ErrorTrace {
    @Id
    private String id;
    private LocalDateTime createdAt;
    private String stackTrace;
}
