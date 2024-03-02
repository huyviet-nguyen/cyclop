package com.tbot.cyclop.Cyclop.kafka;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.Map;

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
}
