package sandbox_akka

import java.util
import java.time.temporal.ChronoUnit
import collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent._
import scala.util._
import java.util.Properties
import org.apache.kafka.streams.KafkaStreams.State
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
import io.circe._
import io.circe.literal._
import io.circe.parser._
import CirceSupport._
import io.circe.generic.auto._

object Main {
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

//    validate()

    val route =
      path("validations") {
        post {
          entity(as[Json]) { input =>
            input.hcursor.downField("id").as[String] match {
              case Right(id) => {
                if (PoolControl.atomicIndex.get() != -1 && receiverStream.state() == State.RUNNING) {
                  Producer.produce(producer, host, processorTopicInput, id, input)

                  onComplete(waitReply(id)) {
                    case Success(value) => {
                      complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, value)))
                    }
                    case Failure(ex) => {
                      val resp = json"""{"error": ${ ex.getMessage }}"""
                      complete(StatusCodes.UnprocessableEntity, resp)
                    }
                  }
                } else {
                  val resp = json"""{"error": "not_ready"}"""
                  complete(StatusCodes.UnprocessableEntity, resp)
                }
              }
              case Left(_) => {
                val resp = json"""{"error": "no_id"}"""
                complete(StatusCodes.UnprocessableEntity, resp)
              }
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

//  private  def validate(): Unit = {
//    val polygonSchema: Schema = Schema.load(
//      """
//        {
//          "type": "object",
//          "properties": {
//            "type": {
//              "type": "string",
//              "const": "Polygon"
//            },
//            "coordinates": {
//              "type": "array",
//              "items": {
//                "type": "array",
//                "minItems": 2,
//                "maxItems": 3
//              }
//            }
//          }
//        }
//      """
//    )
//
//    val good = """{"type": "Polygon", "coordinates": [[0, 0], [1, 0], [1, 1], [0, 1]] }"""
//  }
}
