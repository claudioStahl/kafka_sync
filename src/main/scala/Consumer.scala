package kafka_sync

import scala.util._
import scala.concurrent._
import scala.concurrent.duration._
import akka.util.Timeout
import java.time.Duration
import java.util.Properties
import java.util.Collections
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.errors.WakeupException
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import com.typesafe.scalalogging.Logger

object Consumer {
  val logger = Logger(getClass.getName)

  def launchConsumer(host: String, poolIndex: Int): Unit = {
    implicit val system: ActorSystem[SpawnProtocol.Command] = Main.system
    implicit val executionContext: ExecutionContext = system.executionContext
    implicit val timeout: Timeout = 2.seconds

    val applicationName = sys.env("APPLICATION_NAME")
    val topic = applicationName + "_responses_p" + poolIndex.toString

    val props = new Properties()
    props.put("bootstrap.servers", sys.env("KAFKA_SERVERS"))
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    props.put("group.id", applicationName + "_" + host)

    val thread = new Thread {
      override def run {
        val mainThread = Thread.currentThread
        val consumer = new KafkaConsumer[String, String](props)
        consumer.subscribe(Collections.singletonList(topic))

        Runtime.getRuntime.addShutdownHook(new Thread {
          override def run(): Unit = {
            logger.warn("Consumer Shutdown Starting");
            consumer.wakeup()

            try mainThread.join
            catch {
              case e: InterruptedException =>
                e.printStackTrace()
            }
          }
        })

        try {
          while (true) {
            val records = consumer.poll(Duration.ofMillis(5L))
            records.iterator().forEachRemaining { record: ConsumerRecord[String, String] =>
              val key = ServiceKey[RequestActor.Message](record.key)
              val receptionistFulture = Receptionist.get(system).ref.ask(Receptionist.Find(key))

              receptionistFulture.onComplete {
                case Success(listing: Receptionist.Listing) => {
                  val instances = listing.serviceInstances(key)
                  instances.foreach { actor =>
                    actor ! RequestActor.Reply(record.value)
                  }
                }
                case Failure(ex) => {
                  logger.error("Receptionist.Find: " + ex.getMessage)
                }
              }
            }
          }
        } catch {
          case _: WakeupException => Nil
        } finally {
          consumer.close();
          logger.warn("Consumer closed");
        }
      }
    }

    thread.start
  }
}
