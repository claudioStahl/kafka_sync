package claudiostahl

import java.util.Properties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{Deserializer, Serializer, Serde => JSerde, Serdes => JSerdes}
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.processor.{RecordContext, TopicNameExtractor}
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala._
import org.apache.kafka.streams.scala.kstream._
import org.apache.kafka.streams.scala.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.kstream.GlobalKTable
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.state.{QueryableStoreTypes, ReadOnlyKeyValueStore}
import org.apache.kafka.streams.StoreQueryParameters

object ReceiverStream extends JsonSupport {
  import Serdes._

  def buildStream(host: String): Unit = {
    val config: Properties = new Properties
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "sandbox_akka_receiver2")
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1)

    // we disable the cache to demonstrate all the "steps" involved in the transformation - not recommended in prod
    config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0")

    val builder: StreamsBuilder = new StreamsBuilder

    implicit val PoolControlInput = new JSONSerde[PoolControlInput]
    implicit val poolControlIndexSerde = new JSONSerde[PoolControlIndex]
    implicit val matererlized: Materialized[String, PoolControlIndex, ByteArrayKeyValueStore] = Materialized.as("aggregated-stream-store")
    val globalIndexMatererlized: Materialized[String, PoolControlIndex, ByteArrayKeyValueStore] = Materialized.as("global-index")

    val controlInputs: KStream[String, PoolControlInput] = builder.stream[String, PoolControlInput]("sandbox_akka_pool_control_input")
    controlInputs.peek((k, v) => println("peek", k, v))

    val controlRequests = controlInputs
      .groupByKey
      .aggregate[PoolControlIndex](PoolControlIndex("", 0))((_, input, acc) => PoolControlIndex(input.host, acc.index + 1))

    controlRequests.toStream.map((k, v) => (v.host, v)).to("sandbox_akka_pool_control_index")


//    val indexes: KStream[String, PoolControlIndex] = builder.stream[String, PoolControlIndex]("sandbox_akka_pool_control_index")
//    indexes.peek((k, v) => println("peek", v))

    val globalIndexes: GlobalKTable[String, PoolControlIndex] = builder.globalTable[String, PoolControlIndex]("sandbox_akka_pool_control_index", globalIndexMatererlized)
    println("queryableStoreName", globalIndexes.queryableStoreName())


    //    , Materialized.`with`(Serde[String], Serde[Int]
    //
    //    controlRequests.toStream.to("sandbox_akka_pool_control_index", Produced.`with`(Serde[String], Serde[Int]))
    //, Produced.`with`(Serdes.String(), Serdes.Integer())

    // controlRequests.toStream()

    //    controlRequests.

    //    val outputs: KStream[String, ValidationResponseWithMetadata] = builder.stream("validation_output", Consumed.`with`(Serdes.String(), new JsonSerde[ValidationResponseWithMetadata]))
    //
    //    outputs.to(
    //      //      (key: String, value: ValidationResponseWithMetadata, recordContext: RecordContext) => "sandbox_akka_responses_" + value.metadata.host,
    //      //      (key: String, value: ValidationResponseWithMetadata, recordContext: RecordContext) => "sandbox_akka_responses_node1",
    //      "sandbox_akka_responses_node1",
    //      Produced.`with`(Serdes.String(), new JsonSerde[ValidationResponseWithMetadata]).withName("sink_responses")
    //    )
    //
    //    val responses: KStream[String, String] = builder.stream[String, String]("sandbox_akka_responses_" + host)
    //    responses.peek { (k, v) => println(v) }

    val topology = builder.build()

    //    topology.addSink("private_node_topic", (key: String, value: ValidationResponseWithMetadata, recordContext: RecordContext) => "sandbox_akka_responses_" + value.metadata.host, "sink_responses")

    //    println("topology", topology.describe())
    //
    val streams: KafkaStreams = new KafkaStreams(topology, config)
    streams.start()

    val view: ReadOnlyKeyValueStore[String, PoolControlIndex] = streams.store(StoreQueryParameters.fromNameAndType(globalIndexes.queryableStoreName(), QueryableStoreTypes.keyValueStore[String, PoolControlIndex]()))

    val thread = new Thread {
      override def run {
        while (view.get(host) == null) {
          Thread.sleep(100);
        }
        println("last_index=", view.get(host))
      }
    }

    thread.start

//    val values = view.all()
//    while (values.hasNext) {
//      println("value=", values.next())
//    }

//    while (true) {

//          .forEachRemaining((k: String, v: PoolControlIndex) => println("all=", k, v))
//        println("all=", view.all())
//        Thread.sleep(100);
//    }

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        streams.close()
      }
    })
  }
}

//object ProcessorStream extends JsonSupport {
//  def buildStream(host: String): Unit = {
//    val config: Properties = new Properties
//    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "sandbox_akka_processor")
//    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
//    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
//    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String.getClass)
//    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String.getClass)
//
//    // we disable the cache to demonstrate all the "steps" involved in the transformation - not recommended in prod
////    config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0")
//
//    val builder: StreamsBuilder = new StreamsBuilder
//
//    val inputs: KStream[String, ValidationInputWithMetadata] = builder.stream("validation_input", Consumed.`with`(Serdes.String(), new JsonSerde[ValidationInputWithMetadata]))
//
//    val processedInputs: KStream[String, ValidationResponseWithMetadata] = inputs.mapValues(input => ValidationResponseWithMetadata(input.id, true, input.metadata))
//
//    processedInputs.to("validation_output", Produced.`with`(Serdes.String(), new JsonSerde[ValidationResponseWithMetadata]))
//
//    val topology = builder.build()
//
//    println("topology", topology.describe())
//
//    val streams: KafkaStreams = new KafkaStreams(topology, config)
//    streams.start()
//
//    Runtime.getRuntime.addShutdownHook(new Thread {
//      override def run(): Unit = {
//        streams.close()
//      }
//    })
//  }
//}
