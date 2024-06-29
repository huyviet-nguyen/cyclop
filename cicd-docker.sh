#!/bin/bash

sh mvnw clean package;

# Build and tag the publisher Docker image
#docker build -f publisher.Dockerfile --platform linux/amd64 -t huyvietjava/tradebot-publisher .
docker build -f placer.Dockerfile --platform linux/amd64 -t huyvietjava/tradebot-consumer .
docker push huyvietjava/tradebot-consumer:latest
#docker push huyvietjava/tradebot-publisher:latest

# SSH into another host and run docker commands
#ssh admin@116.96.87.51 << EOF
#    docker pull huyvietjava/tradebot-consumer:latest
#    docker pull huyvietjava/tradebot-publisher:latest
#    cd Personal/2Tbot/cyclop/deployments
#    docker-compose -f docker-compose.yml down
#    docker-compose -f docker-compose.yml up -d
#EOF

# Uncomment if needed to push images to AWS ECR
#sh push-image.sh "2tbot-publisher";
#sh push-image.sh "2tbot-order-placer";

# Uncomment if needed to redeploy
#sh redeploy.sh;