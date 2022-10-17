#! /bin/bash

create_topic()
{
  docker exec -it $SCRIPT_CONTAINER_NAME bash -c "/usr/bin/kafka-topics --create --replication-factor 1 --zookeeper $SCRIPT_ZOOKEEPER_SERVERS --topic $1 --partitions $2"
}

env_name=${1:-"dev"}

echo "Start with env: ${env_name}"

export $(cat "${env_name}.env" | xargs)

create_topic $PROCESSOR_TOPIC_INPUT $PROCESSOR_TOPIC_PARTITIONS
create_topic $PROCESSOR_TOPIC_OUTPUT $PROCESSOR_TOPIC_PARTITIONS

for ((i=1; i<=$POOL_SIZE; i++)); do
  create_topic "${APPLICATION_NAME}_responses_p${i}" $POOL_PARTITIONS
done
