#!/bin/bash

sh mvnw clean package;

# Build and tag the publisher Docker image
docker build -f publisher.Dockerfile --platform linux/amd64 -t 2tbot-publisher .
docker build -f placer.Dockerfile --platform linux/amd64 -t 2tbot-order-placer .

# Push images to AWS ECR
sh push-image.sh "2tbot-publisher";
sh push-image.sh "2tbot-order-placer";

sh redeploy.sh;

