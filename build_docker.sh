#!/bin/bash

docker_tag="$1"

mvn clean install -Dmaven.test.skip=true

cur_dir=$(pwd)
echo "current dir ${cur_dir}"

#cd ${cur_dir}
#cd dubbo/dubbo-examples/dubbo-discovery-example/dubbo-quick-provider
#bash build_docker.sh ${docker_tag}
#
#cd ${cur_dir}
#cd dubbo/dubbo-examples/dubbo-discovery-example/dubbo-quick-consumer
#bash build_docker.sh ${docker_tag}

cd ${cur_dir}
cd dubbo/dubbo-examples/dubbo-press-example/dubbo-press-provider
bash build_docker.sh ${docker_tag}
