#!/bin/bash

# Build and tag the publisher Docker image
docker build -f publisher.Dockerfile -t 2tbot-publisher .

# Build and tag the placer Docker image
docker build -f placer.Dockerfile -t 2tbot-order-placer .
