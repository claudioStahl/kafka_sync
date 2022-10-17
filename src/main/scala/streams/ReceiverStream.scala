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
import org.apache.kafka.streams.scala.kstream.BranchedKStream
import org.apache.kafka.clients.producer.ProducerConfig
import com.goyeau.kafka.streams.circe.CirceSerdes._
import Serdes._
import io.circe._

object ReceiverStream {
  def buildStream(poolSize: Int): KafkaStreams = {
    val applicationName = sys.env("APPLICATION_NAME")
    val processorTopicOutput = sys.env("PROCESSOR_TOPIC_OUTPUT")

    val config: Properties = new Properties
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationName + "_receiver")
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, sys.env("KAFKA_SERVERS"))
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JSerdes.String.getClass)
    config.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1)
    config.put(ProducerConfig.LINGER_MS_CONFIG, 0)

    val builder: StreamsBuilder = new StreamsBuilder

    val outputs: KStream[String, Json] = builder.stream[String, Json](processorTopicOutput)
    val outputsBranched: BranchedKStream[String, Json]  = outputs.split()

    (1 to poolSize).foreach { i =>
      outputsBranched.branch(
        (k: String, v: Json) => {
          v.hcursor.downField("metadata").downField("poolIndex").as[Int] match {
            case Right(poolIndex) => poolIndex == i
            case Left(_) => false
          }
        },
        Branched.withConsumer(ks => {
          ks.to(applicationName + "_responses_p" + i.toString)
        })
      )
    }

    val topology = builder.build()

    println("ReceiverStream.topology", topology.describe())

    val streams: KafkaStreams = new KafkaStreams(topology, config)

    streams.start()

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        streams.close()
      }
    })

    streams
  }
}
