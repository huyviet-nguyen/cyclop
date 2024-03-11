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
sleep 10;
cd .. ;
cd apps;
kubectl apply -f .;