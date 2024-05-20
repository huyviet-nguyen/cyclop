#!/bin/bash

kubectl config use-context arn:aws:eks:ap-southeast-2:055540832687:cluster/k8s-2t;
kubectl delete deployment --all;
kubectl delete service --all;
sleep 10;

cd dev-ops/k8s/kafka;
kubectl apply -f zookeeper-deployment.yaml;
kubectl apply -f zookeeper-service.yaml;
sleep 10;
kubectl apply -f kafka-deployment.yaml;
kubectl apply -f kafka-service.yaml;
sleep 50
# update partition
kafka_pod=$(kubectl get pods -l app=kafka -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it "$kafka_pod" -- sh -c '/usr/bin/kafka-topics --create --if-not-exists --bootstrap-server kafka:9092 --replication-factor 1 --partitions 300 --topic kline --config retention.ms=10000 && /usr/bin/kafka-topics --describe --bootstrap-server kafka:9092 --topic kline '
sleep 10;
cd .. ;
cd apps;
kubectl apply -f order-placer-deployment.yaml;
sleep 50;
kubectl apply -f publisher-service.yml;
kubectl apply -f publisher-deployment.yaml;
