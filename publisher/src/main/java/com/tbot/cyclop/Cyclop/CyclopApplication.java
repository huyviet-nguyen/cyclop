package com.tbot.cyclop.Cyclop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CyclopApplication {
    public static void main(String[] args) {
        SpringApplication.run(CyclopApplication.class, args);
    }

}
