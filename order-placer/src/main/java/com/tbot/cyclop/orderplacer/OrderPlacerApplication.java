package com.tbot.cyclop.orderplacer;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.OrderPlacementAck;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.function.Function;

@SpringBootApplication
public class OrderPlacerApplication {

    @Bean
    public Function<KStream<String, KlineData>, KStream<String, OrderPlacementAck>> process(){
        return stringKlineDataKStream -> stringKlineDataKStream.mapValues(data -> new OrderPlacementAck());
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderPlacerApplication.class, args);
    }

}
