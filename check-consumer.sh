#!/bin/bash

# Function to execute the Kafka command and update the console log
execute_kafka_command() {
    kafka_pod=$(kubectl get pods -l app=kafka -o jsonpath='{.items[0].metadata.name}')
    new_output=$(kubectl exec "$kafka_pod" -- sh -c '/usr/bin/kafka-consumer-groups --bootstrap-server kafka:9092 --describe --group order-placer')

    # Check if there's new output
    if [ "$new_output" != "$current_output" ]; then
        clear
        echo "$new_output"
        current_output="$new_output"
    fi
}

# Initialize current_output variable
current_output=""

# Main loop to execute the Kafka command every 3 seconds
while true; do
    execute_kafka_command
    sleep 1
done
