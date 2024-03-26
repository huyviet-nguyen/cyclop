package com.tbot.cyclop.Cyclop.kafka;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.*;

@Configuration
public class KafkaBeanConfig {

    @Bean
    public KafkaSender<String, KlineData> kafkaSender(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        return KafkaSender.create(SenderOptions.<String, KlineData>create(props).maxInFlight(1024));
    }

    @Bean
    public KafkaSender<String, String> errorKafkaSender(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        return KafkaSender.create(SenderOptions.<String, String>create(props).maxInFlight(1024));
    }

    @Bean
    @Qualifier("activeMap")
    public Map<String, HashMap<String, Set<String>>> activeMap() {
        List<String> supportedInterval = List.of("1", "5", "15", "30", "60");
        Map<String, HashMap<String, Set<String>>> activeMap = new HashMap<>();
        activeMap.put("mexc", new HashMap<>());
        activeMap.put("bybit", new HashMap<>());
        for (String interval : supportedInterval) {
            activeMap.get("mexc").put(interval, new HashSet<>());
            activeMap.get("bybit").put(interval, new HashSet<>());
        }
        return activeMap;
    }

}
