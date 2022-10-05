#! /bin/bash

docker exec -it global_kafka bash -c "/usr/bin/kafka-topics --create --topic validation_input --partitions 1 --replication-factor 1 --zookeeper zookeeper:2181"

docker exec -it global_kafka bash -c "/usr/bin/kafka-topics --create --topic validation_output --partitions 1 --replication-factor 1 --zookeeper zookeeper:2181"

docker exec -it global_kafka bash -c "/usr/bin/kafka-topics --create --topic sandbox_akka_responses_p1 --partitions 1 --replication-factor 1 --zookeeper zookeeper:2181"
docker exec -it global_kafka bash -c "/usr/bin/kafka-topics --create --topic sandbox_akka_responses_p2 --partitions 1 --replication-factor 1 --zookeeper zookeeper:2181"
docker exec -it global_kafka bash -c "/usr/bin/kafka-topics --create --topic sandbox_akka_responses_p3 --partitions 1 --replication-factor 1 --zookeeper zookeeper:2181"

docker exec -it global_kafka bash -c "/usr/bin/kafka-topics --create --topic sandbox_akka_pool_control_input --partitions 1 --replication-factor 1 --zookeeper zookeeper:2181"
docker exec -it global_kafka bash -c "/usr/bin/kafka-topics --create --topic sandbox_akka_pool_control_index --partitions 1 --replication-factor 1 --zookeeper zookeeper:2181"
