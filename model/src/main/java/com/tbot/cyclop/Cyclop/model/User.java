package com.tbot.cyclop.Cyclop.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Document(collection = "user")
public class User implements Serializable {
    @Id
    private String id;
    private String name;
    private String username;
    private String password;
    private String avatar;
    private String status;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

