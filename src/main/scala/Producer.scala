package sandbox_akka

import java.util.Properties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord

object Producer extends JsonSupport {
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

  def produce(producer: KafkaProducer[String, String], host: String, topic: String, input: ValidationInput): Unit = {
    val index = PoolControl.atomicIndex.get()
    val inputWithMetadata = ValidationInputWithMetadata(input.id, input.amount, MessageMetadata(host, index))
    val message = validationInputWithMetadataFormat.write(inputWithMetadata).compactPrint
    val record = new ProducerRecord[String, String](topic, input.id, message)
    producer.send(record)
  }
}
