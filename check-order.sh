kafka_pod=$(kubectl get pods -l app=kafka -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it "$kafka_pod" -- sh -c '/usr/bin/kafka-console-consumer --bootstrap-server kafka:9092 --topic order --partition 0 --offset latest --max-messages 100'
