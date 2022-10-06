#! /bin/bash

for topic in validation_input validation_output sandbox_akka_pool_control_input sandbox_akka_pool_control_index sandbox_akka_responses_p1 sandbox_akka_responses_p2 sandbox_akka_responses_p3
do
  docker exec -it global_kafka bash -c "/usr/bin/kafka-topics --delete --zookeeper zookeeper:2181 --topic ${topic}"
done
