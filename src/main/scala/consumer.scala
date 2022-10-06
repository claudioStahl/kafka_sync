package claudiostahl

import scala.util._
import scala.concurrent._
import scala.concurrent.duration._
import akka.util.Timeout
import java.time.Duration
import java.util.Properties
import java.util.Collections
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, KafkaConsumer}
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._

object Consumer {
  def launchConsumer(host: String, poolIndex: Int): Unit = {
    implicit val system: ActorSystem[SpawnProtocol.Command] = Main.system
    implicit val executionContext: ExecutionContext = system.executionContext
    implicit val timeout: Timeout = 2.seconds

    val topic = "sandbox_akka_responses_p" + poolIndex.toString

    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092")
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    props.put("group.id", "sandbox_akka_" + host)

    val consumer = new KafkaConsumer[String, String](props)
    consumer.subscribe(Collections.singletonList(topic))

    val thread = new Thread {
      override def run {
        while (true) {
          val records = consumer.poll(Duration.ofMillis(100L))
          records.iterator().forEachRemaining { record: ConsumerRecord[String, String] =>
            val key = ServiceKey[RequestActor.Message](record.key)
            val receptionistFulture = Receptionist.get(system).ref.ask(Receptionist.Find(key))

            receptionistFulture.onComplete {
              case Success(listing: Receptionist.Listing) =>
                val instances = listing.serviceInstances(key)
                val actor = instances.iterator.next()
                actor ! RequestActor.Reply(record.value)

              case Failure(ex) =>
                println("An error has occurred: " + ex.getMessage)
            }

            println(
              s"""offset=${record.offset}, partition=${record.partition}, key=${record.key}, value=${record.value}, schema=${record.value}""".stripMargin
            )
          }
        }
      }
    }

    thread.start
  }
}
