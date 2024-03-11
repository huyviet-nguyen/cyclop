#!/bin/bash

# ECR repository host
REPO_HOST="055540832687.dkr.ecr.ap-southeast-2.amazonaws.com"

# Authentication token
AUTH_TOKEN=$(aws ecr get-login-password --region ap-southeast-2)

# Check if Docker image argument is provided
if [ -z "$1" ]; then
    echo "Usage: $0 <docker-image>"
    exit 1
fi

# Docker image passed as argument
DOCKER_IMAGE="$1"

# Login to ECR using authentication token
echo "Logging in to Amazon ECR..."
echo "$AUTH_TOKEN" | docker login --username AWS --password-stdin $REPO_HOST/"$DOCKER_IMAGE"

# Tag the local Docker image with the ECR repository URI
TAG="latest"
docker tag "$DOCKER_IMAGE" $REPO_HOST/"$DOCKER_IMAGE":"$TAG"

# Push the tagged Docker image to the ECR repository
echo "Pushing Docker image to Amazon ECR... : $DOCKER_IMAGE"
docker push $REPO_HOST/"$DOCKER_IMAGE":"$TAG"

# Verify the image has been pushed successfully
if [ $? -eq 0 ]; then
    echo "Docker image successfully pushed to Amazon ECR."
else
    echo "Failed to push Docker image to Amazon ECR."
fi
