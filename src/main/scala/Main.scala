package claudiostahl

import java.time.Duration
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._
import scala.concurrent._
import scala.util._
import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.{Deserializer, Serde, Serdes, Serializer}
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream._
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.{KafkaStreams, KeyValue, StreamsBuilder, StreamsConfig}
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.SpawnProtocol
import akka.actor.typed.ActorSystem
import akka.actor.typed.Props
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.pattern.ask
import akka.util.Timeout
import akka.util.ByteString
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

class JsonSerializer[T >: Null <: Any : JsonFormat] extends Serializer[T] {
  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()

  override def serialize(topic: String, data: T): Array[Byte] = {
    data.toJson.compactPrint.getBytes
  }

  override def close(): Unit = ()
}

class JsonDeserializer[T >: Null <: Any : JsonFormat] extends Deserializer[T] {
  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()

  override def close(): Unit = ()

  override def deserialize(topic: String, data: Array[Byte]): T = {
    ByteString(data).utf8String.parseJson.convertTo[T]
  }
}

class JsonSerde[T >: Null <: Any : JsonFormat] extends Serde[T] {
  override def deserializer(): Deserializer[T] = new JsonDeserializer[T]

  override def serializer(): Serializer[T] = new JsonSerializer[T]

  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()

  override def close(): Unit = ()
}

// domain model
final case class ValidationInput(id: String, amount: Int)
final case class ValidationResponse(id: String, is_fraud: Boolean)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val validationInputFormat = jsonFormat2(ValidationInput)
  implicit val validationResponseFormat = jsonFormat2(ValidationResponse)
}

object MainActor {
  def apply(): Behavior[SpawnProtocol.Command] =
    Behaviors.setup { context =>
      // Start initial tasks
      // context.spawn(...)

      SpawnProtocol()
    }
}

object RequestActor {
  sealed trait Message

  final case class Wait(ref: ActorRef[String]) extends Message

  final case class Reply(value: String) extends Message

  case object Hello extends Message

  case object IdentifyYourself extends Message

  private case object Timeout extends Message

  sealed trait Data

  case object Uninitialized extends Data

  final case class Initialized(target: ActorRef[String]) extends Data

  def apply(id: String): Behavior[Message] = Behaviors.setup { context =>
    val key = ServiceKey[Message](id)
    context.system.receptionist ! Receptionist.Register(key, context.self)

    handle(id, Uninitialized)
  }

  private def handle(id: String, data: Data): Behavior[Message] = Behaviors.setup { context =>
    Behaviors.receiveMessage[Message] { message =>
      (message, data) match {
        case (IdentifyYourself, _) =>
          println(IdentifyYourself, context.self)
          Behaviors.same
        case (Hello, _) =>
          println("hello back at you")
          Behaviors.unhandled
        case (Wait(ref), Uninitialized) =>
          println("Wait", id)
          handle(id, Initialized(ref))
        case (Reply(value), Initialized(ref)) =>
          ref ! value
          Behaviors.stopped
        case _ =>
          Behaviors.unhandled
      }
    }
  }
}

object Producer extends JsonSupport {
  def buildProducer(): KafkaProducer[String, String] = {
    val kafkaProps = new Properties()
    kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    kafkaProps.put(
      "key.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    kafkaProps.put(
      "value.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    kafkaProps.put("acks", "all")

    new KafkaProducer[String, String](kafkaProps)
  }

  def produce(producer: KafkaProducer[String, String], topic: String, input: ValidationInput): Unit = {
    val message = validationInputFormat.write(input).compactPrint
    var record = new ProducerRecord[String, String](topic, input.id, message)
    producer.send(record)
  }
}

object ProcessorStream extends JsonSupport {
  def buildStream(): Unit = {
    val config: Properties = new Properties
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "sandbox_akka_processor")
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092")
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String.getClass)
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String.getClass)

    // we disable the cache to demonstrate all the "steps" involved in the transformation - not recommended in prod
    config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0")

    val builder: StreamsBuilder = new StreamsBuilder

    val inputs: KStream[String, ValidationInput] = builder.stream("validation_input", Consumed.`with`(Serdes.String(), new JsonSerde[ValidationInput]))

    val responses: KStream[String, ValidationResponse] = inputs.mapValues(input => ValidationResponse(input.id, true))

    responses.to("validation_output", Produced.`with`(Serdes.String(), new JsonSerde[ValidationResponse]))

    val streams: KafkaStreams = new KafkaStreams(builder.build(), config)
    streams.start()

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        streams.close()
      }
    })
  }
}


object ReceiverStream {
  def buildStream(): Unit = {
    val config: Properties = new Properties
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "sandbox_akka_receiver")
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092")
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String.getClass)
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String.getClass)
    config.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1)

    // we disable the cache to demonstrate all the "steps" involved in the transformation - not recommended in prod
    config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0")

    val builder: StreamsBuilder = new StreamsBuilder

    val inputs: KStream[String, String] = builder.stream[String, String]("validation_input")
    val outputs: KStream[String, String] = builder.stream[String, String]("validation_output")

    val joiner: ValueJoiner[String, String, String] = { (input, output) =>
      output
    }
    val joinWindow = JoinWindows.of(Duration.ofSeconds(5L))

    val joined = inputs.join(outputs, joiner, joinWindow).peek { (k, v) => println(v) }

//    joined.to("validation_join")

    val streams: KafkaStreams = new KafkaStreams(builder.build(), config)
    streams.start()

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        streams.close()
      }
    })
  }
}

object Main extends JsonSupport {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(MainActor(), "my-system")
  implicit val executionContext: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = 2.seconds

  def main(args: Array[String]): Unit = {
    val topic = "validation_input"
    val producer = Producer.buildProducer()

    ProcessorStream.buildStream()
    ReceiverStream.buildStream()

    val route =
      path("validations") {
        post {
          entity(as[ValidationInput]) { input =>
            Producer.produce(producer, topic, input)

            onComplete(waitReply(input.id)) {
              case Success(value) => complete(StatusCodes.OK, value)
              case Failure(ex) => complete(StatusCodes.UnprocessableEntity, ex.getMessage)
            }
          }
        }
      }

    Http().newServerAt("localhost", 4000).bind(route)

    println(s"Server now online. Please navigate to http://localhost:4000")
  }

  private def waitReply(id: String): Future[String] = {
    val actorFuture: Future[ActorRef[RequestActor.Message]] = system.ask(SpawnProtocol.Spawn(RequestActor(id), name = id, props = Props.empty, _))

    actorFuture.flatMap { actor =>
      println("actor", actor)
      val resultFuture = actor ? RequestActor.Wait
      actor ! RequestActor.Reply("amazing")
      resultFuture
    }
  }
}
