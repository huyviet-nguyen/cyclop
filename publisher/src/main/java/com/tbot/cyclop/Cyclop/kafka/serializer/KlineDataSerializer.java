package com.tbot.cyclop.Cyclop.kafka.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class KlineDataSerializer implements Serializer<KlineData> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No additional configuration needed
    }

    @Override
    public byte[] serialize(String topic, KlineData data) {
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing MarketData to JSON", e);
        }
    }

    @Override
    public void close() {
        // No resources to release
    }

}
