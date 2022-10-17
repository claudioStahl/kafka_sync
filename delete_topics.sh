#! /bin/bash

delete_topic()
{
  docker exec -it $SCRIPT_CONTAINER_NAME bash -c "/usr/bin/kafka-topics --delete --zookeeper $SCRIPT_ZOOKEEPER_SERVERS --topic $1"
}

env_name=${1:-"dev"}

echo "Start with env: ${env_name}"

export $(cat "${env_name}.env" | xargs)

delete_topic $PROCESSOR_TOPIC_INPUT
delete_topic $PROCESSOR_TOPIC_OUTPUT

for ((i=1; i<=$POOL_SIZE; i++)); do
   delete_topic "${APPLICATION_NAME}_responses_p${i}"
done
