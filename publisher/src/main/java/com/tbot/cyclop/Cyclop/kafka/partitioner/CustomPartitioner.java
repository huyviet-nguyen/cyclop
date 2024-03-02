package com.tbot.cyclop.Cyclop.kafka.partitioner;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CustomPartitioner implements Partitioner {

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        // Ensure the key is a string
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Key must be a string");
        }
        String[] keyParts = ((String) key).split(",");
        if (keyParts.length < 2) {
            throw new IllegalArgumentException("Key format is incorrect. Expected format: sourcePlatform,symbol");
        }
        String sourcePlatform = keyParts[0];
        String symbol = keyParts[1];
        int numPartitions = cluster.partitionCountForTopic(topic);
        String combinedKey = sourcePlatform + symbol;
        return Math.abs(combinedKey.hashCode() % numPartitions);

    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
