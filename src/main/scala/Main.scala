package sandbox_akka

import java.util
import java.time.temporal.ChronoUnit
import collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent._
import scala.util._
import java.util.Properties
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
import org.apache.kafka.streams.KafkaStreams.State

object Main extends JsonSupport {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(MainActor(), "app")
  implicit val executionContext: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = 2.seconds

  def main(args: Array[String]): Unit = {
    val host = sys.env("HOST")
    val applicationName = sys.env("APPLICATION_NAME")
    val processorTopicInput = sys.env("PROCESSOR_TOPIC_INPUT")
    val serverPort = sys.env("SERVER_PORT").toInt
    val poolSize = sys.env("POOL_SIZE").toInt

    val producer = Producer.buildProducer()

    PoolControl.init(poolSize, host)
    val receiverStream = ReceiverStream.buildStream(poolSize)
    ProcessorStream.buildStream()

    val route =
      path("validations") {
        post {
          entity(as[ValidationInput]) { input =>
            if (PoolControl.atomicIndex.get() != -1 && receiverStream.state() == State.RUNNING) {
              Producer.produce(producer, host, processorTopicInput, input)

              onComplete(waitReply(input.id)) {
                case Success(value) => {
                  complete(StatusCodes.OK, value)
                }
                case Failure(ex) => complete(StatusCodes.UnprocessableEntity, ex.getMessage)
              }
            } else {
              complete(StatusCodes.UnprocessableEntity, "not_ready")
            }
          }
        }
      }

    Http().newServerAt("localhost", serverPort).bind(route)

    println(s"Server now online. Please navigate to http://localhost:$serverPort")
  }

  private def waitReply(id: String): Future[String] = {
    val actorFuture: Future[ActorRef[RequestActor.Message]] = system.ask(SpawnProtocol.Spawn(RequestActor(id), name = id, props = Props.empty, _))

    actorFuture.flatMap { actor =>
      val resultFuture = actor ? RequestActor.Wait

      resultFuture
    }
  }
}
