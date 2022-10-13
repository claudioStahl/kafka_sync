package claudiostahl

import java.util.Properties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{Serdes => JSerdes}
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala._
import org.apache.kafka.streams.scala.kstream._
import org.apache.kafka.streams.scala.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.kstream.GlobalKTable
import org.apache.kafka.streams.state.{QueryableStoreTypes, ReadOnlyKeyValueStore}
import org.apache.kafka.streams.StoreQueryParameters

object PoolControlStream extends JsonSupport {
  import Serdes._

  def buildStream(host: String): Unit = {
    val poolSize = 3

    val config: Properties = new Properties
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "sandbox_akka_poolcontrol_v" + Main.version.toString)
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1)

    // we disable the cache to demonstrate all the "steps" involved in the transformation - not recommended in prod
    // config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0")

    implicit val PoolControlInput = new JSONSerde[PoolControlInput]
    implicit val poolControlIndexSerde = new JSONSerde[PoolControlIndex]
    implicit val validationResponseWithMetadata = new JSONSerde[ValidationResponseWithMetadata]
    implicit val matererlized: Materialized[String, PoolControlIndex, ByteArrayKeyValueStore] = Materialized.as("aggregated_stream_store_v" + Main.version.toString)
    val globalIndexMatererlized: Materialized[String, PoolControlIndex, ByteArrayKeyValueStore] = Materialized.as("global_index_v" + Main.version.toString)

    val builder: StreamsBuilder = new StreamsBuilder

    val controlInputs: KStream[String, PoolControlInput] = builder.stream[String, PoolControlInput]("sandbox_akka_pool_control_input")

    val controlRequests = controlInputs
      .groupByKey
      .aggregate[PoolControlIndex](PoolControlIndex("", 0)) { (_, input, acc) =>
        var newIndex = acc.index + 1
        if (newIndex > poolSize) newIndex = 1
        PoolControlIndex(input.host, newIndex)
      }

    controlRequests.toStream.map((k, v) => (v.host, v)).to("sandbox_akka_pool_control_index")

    val globalIndexes: GlobalKTable[String, PoolControlIndex] = builder.globalTable[String, PoolControlIndex]("sandbox_akka_pool_control_index", globalIndexMatererlized)

    val topology = builder.build()
    println("PoolControlStream.topology", topology.describe())
    val streams: KafkaStreams = new KafkaStreams(topology, config)
    streams.start()

    val view: ReadOnlyKeyValueStore[String, PoolControlIndex] = streams.store(StoreQueryParameters.fromNameAndType(globalIndexes.queryableStoreName(), QueryableStoreTypes.keyValueStore[String, PoolControlIndex]()))

    val thread = new Thread {
      override def run {
        while (view.get(host) == null) {
          Thread.sleep(100);
        }

        val poolControlInput: PoolControlIndex = view.get(host)
        PoolControl.index = poolControlInput.index
        println("index=", poolControlInput)
        Consumer.launchConsumer(host, poolControlInput.index)
      }
    }

    thread.start

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        streams.close()
      }
    })
  }
}