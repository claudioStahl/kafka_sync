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
import akka.http.scaladsl.server._
import io.circe._
import io.circe.literal._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.schema.Schema
import CirceSupport._
import cats.data.Validated.{Invalid, Valid}

case class ErrorResponse(error: String, message: Option[String] = None)

object Main {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(MainActor(), "app")
  implicit val executionContext: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = 2.seconds

  def myRejectionHandler =
    RejectionHandler.newBuilder()
      .handle {
        case error => {
          complete(StatusCodes.BadRequest, ErrorResponse(error.getClass.getSimpleName))
        }
      }
      .handleNotFound {
        complete(StatusCodes.NotFound, ErrorResponse("NotFound"))
      }
      .result()

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

    val inputSchema: Schema = Schema.load(
      json"""
        {
          "type": "object",
          "required": [
              "id",
              "amount"
          ],
          "properties": {
              "id": {
                  "type": "string",
                  "title": "The id Schema",
                  "examples": [
                      "abc"
                  ]
              },
              "amount": {
                  "type": "integer",
                  "title": "The amount Schema",
                  "examples": [
                      10
                  ]
              }
          },
          "examples": [{
              "id": "abc",
              "amount": 10
          }]
        }
      """
    )

    val route = handleRejections(myRejectionHandler) {
      path("validations") {
        post {
          entity(as[Json]) { input =>
            println("validate=", inputSchema.validate(input))
            inputSchema.validate(input) match {
              case Valid(_) => {
                input.hcursor.downField("id").as[String] match {
                  case Right(id) => {
                    if (PoolControl.atomicIndex.get() != -1 && receiverStream.state() == State.RUNNING) {
                      Producer.produce(producer, host, processorTopicInput, id, input)

                      onComplete(waitReply(id)) {
                        case Success(value) => {
                          complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, value)))
                        }
                        case Failure(ex) => {
                          complete(StatusCodes.UnprocessableEntity, ErrorResponse("ValidationFailed", Some(ex.getMessage)))
                        }
                      }
                    } else {
                      complete(StatusCodes.UnprocessableEntity, ErrorResponse("NotReady"))
                    }
                  }
                  case Left(_) => {
                    complete(StatusCodes.BadRequest, ErrorResponse("NoId"))
                  }
                }
              }
              case Invalid(er) => {
                val message = er.map(_.getMessage).head
                complete(StatusCodes.BadRequest, ErrorResponse("InvalidInput", Some(message)))
              }
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
}
