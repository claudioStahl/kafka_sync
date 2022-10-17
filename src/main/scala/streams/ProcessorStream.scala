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
import com.goyeau.kafka.streams.circe.CirceSerdes._
import io.circe._
import io.circe.literal._
import Serdes._

object ProcessorStream {
  def buildStream(): Unit = {
    val applicationName = sys.env("APPLICATION_NAME")
    val processorTopicInput = sys.env("PROCESSOR_TOPIC_INPUT")
    val processorTopicOutput = sys.env("PROCESSOR_TOPIC_OUTPUT")

    val config: Properties = new Properties
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationName + "_processor")
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, sys.env("KAFKA_SERVERS"))
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(ProducerConfig.LINGER_MS_CONFIG, 0)

    val builder: StreamsBuilder = new StreamsBuilder

    val inputs: KStream[String, Json] = builder.stream[String, Json](processorTopicInput)

    val processedInputs: KStream[String, Json] = inputs.mapValues((key, value) => {
      val metadata = value.hcursor.get[Json]("metadata").toOption.get
      json"""{"id": $key, "is_fraud": true, "metadata": $metadata}"""
    })

    processedInputs.to(processorTopicOutput)

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
