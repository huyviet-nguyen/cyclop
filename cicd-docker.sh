#!/bin/bash

#sh mvnw clean package;

# Build and tag the publisher Docker image
docker build -f publisher.Dockerfile --platform linux/amd64 -t huyvietjava/tradebot-publisher .
docker build -f placer.Dockerfile --platform linux/amd64 -t huyvietjava/tradebot-consumer .
docker push huyvietjava/tradebot-consumer:latest
docker push huyvietjava/tradebot-publisher:latest
# Push images to AWS ECR
#sh push-image.sh "2tbot-publisher";
#sh push-image.sh "2tbot-order-placer";

#sh redeploy.sh;

