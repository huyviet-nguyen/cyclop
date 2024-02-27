package com.tbot.cyclop.Cyclop.kafka.partitioner;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CustomPartitioner implements Partitioner {

    private static final int DEFAULT_PARTITION = 20; // Default partition number

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        // Logic to determine the partition number
        int numPartitions = cluster.partitionCountForTopic(topic);
        // Return the default partition number if the topic doesn't exist
        return numPartitions > 0 ? Math.abs(key.hashCode() % numPartitions) : DEFAULT_PARTITION;
    }

    @Override
    public void close() {
        // Clean-up resources if necessary
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // Configure the partitioner if necessary
    }
}
