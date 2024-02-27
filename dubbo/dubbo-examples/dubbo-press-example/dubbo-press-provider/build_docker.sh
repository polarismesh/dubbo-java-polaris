#!/bin/bash
docker_tag="$1"

docker buildx build -f ./Dockerfile  --build-arg version=0.1.0-3.2.7-SNAPSHOT  -t polarismesh/dubbo-press-provider:0.1.0-3.2.7-SNAPSHOT --platform linux/amd64 --push ./
