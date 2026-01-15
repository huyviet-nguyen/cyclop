#!/bin/bash

#sh mvnw clean package;

# Build and tag the publisher Docker image
docker build -f publisher.Dockerfile --platform linux/amd64 -t [ECR_REPO_NAME]-publisher .
docker build -f placer.Dockerfile --platform linux/amd64 -t [ECR_REPO_NAME]-order-placer .

# Push images to AWS ECR
sh push-image.sh "[ECR_REPO_NAME]-publisher";
sh push-image.sh "[ECR_REPO_NAME]-order-placer";

sh redeploy.sh;

