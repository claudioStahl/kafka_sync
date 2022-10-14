package sandbox_akka

import java.util.Properties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{Serdes => JSerdes}
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala._
import org.apache.kafka.streams.scala.kstream._
import org.apache.kafka.streams.scala.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.clients.producer.ProducerConfig

object ProcessorStream extends JsonSupport {
  import Serdes._

  def buildStream(): Unit = {
    val config: Properties = new Properties
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "sandbox_akka_processor_v" + Main.version.toString)
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(ProducerConfig.LINGER_MS_CONFIG, 0)

    // we disable the cache to demonstrate all the "steps" involved in the transformation - not recommended in prod
    // config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0")

    implicit val validationInputWithMetadata = new JSONSerde[ValidationInputWithMetadata]
    implicit val validationResponseWithMetadata = new JSONSerde[ValidationResponseWithMetadata]

    val builder: StreamsBuilder = new StreamsBuilder

    val inputs: KStream[String, ValidationInputWithMetadata] = builder.stream[String, ValidationInputWithMetadata]("validation_input")

    val processedInputs: KStream[String, ValidationResponseWithMetadata] = inputs.mapValues(input => {
      println("[time]", "[ProcessorStream.mapValues]", System.currentTimeMillis())
      ValidationResponseWithMetadata(input.id, true, input.metadata)
    })

    processedInputs.to("validation_output")

    val topology = builder.build()

    println("ProcessorStream.topology", topology.describe())

    val streams: KafkaStreams = new KafkaStreams(topology, config)
    streams.start()

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        streams.close()
      }
    })
  }
}
