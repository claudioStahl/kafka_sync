package sandbox_akka

import java.util.Properties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import spray.json._

object Producer {
  def buildProducer(): KafkaProducer[String, String] = {
    val config = new Properties()
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, sys.env("KAFKA_SERVERS"))
    config.put(
      "key.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    config.put(
      "value.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    config.put("acks", "0")

    val producer = new KafkaProducer[String, String](config)

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        producer.close()
      }
    })

    producer
  }

  def produce(producer: KafkaProducer[String, String], host: String, topic: String, id: String, input: JsObject): Unit = {
    val index = PoolControl.atomicIndex.get()
    val inputWithMetadata = JsObject(input.fields + ("metadata" -> JsObject("host" -> JsString(host), "poolIndex" -> JsNumber(index))))
    val message = inputWithMetadata.compactPrint
    val record = new ProducerRecord[String, String](topic, id, message)
    producer.send(record)
  }
}
