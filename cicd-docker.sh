#!/bin/bash

sh mvnw clean package;

# Build and tag the publisher Docker image
docker build -f publisher.Dockerfile --platform linux/amd64 -t [DOCKER_HUB_USERNAME]/tradebot-publisher .
docker build -f placer.Dockerfile --platform linux/amd64 -t [DOCKER_HUB_USERNAME]/tradebot-consumer .
docker push [DOCKER_HUB_USERNAME]/tradebot-consumer:latest
docker push [DOCKER_HUB_USERNAME]/tradebot-publisher:latest

# SSH into another host and run docker commands
#ssh admin@116.96.87.51 << EOF
#    docker pull [DOCKER_HUB_USERNAME]/tradebot-consumer:latest
#    docker pull [DOCKER_HUB_USERNAME]/tradebot-publisher:latest
#    cd Personal/[PROJECT_PATH]/cyclop/deployments
#    docker-compose -f docker-compose.yml down
#    docker-compose -f docker-compose.yml up -d
#EOF

# Uncomment if needed to push images to AWS ECR
#sh push-image.sh "[ECR_REPO_NAME]-publisher";
#sh push-image.sh "[ECR_REPO_NAME]-order-placer";

# Uncomment if needed to redeploy
#sh redeploy.sh;