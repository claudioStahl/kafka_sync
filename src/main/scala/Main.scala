package kafka_sync

import scala.io.Source
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
import com.typesafe.scalalogging.Logger

object Main {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(MainActor(), "app")
  implicit val executionContext: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = 2.seconds

  val logger = Logger(getClass.getName)

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
    val jsonSchemaFilePath = sys.env("JSON_SCHEMA_FILE_PATH")
    val serverPort = sys.env("SERVER_PORT").toInt
    val poolSize = sys.env("POOL_SIZE").toInt
    val enableFakerProcessor = sys.env("ENABLE_FAKER_PROCESSOR") == "true"

    val producer = Producer.buildProducer()

    PoolControl.init(poolSize, host)
    val receiverStream = ReceiverStream.buildStream(poolSize)

    if (enableFakerProcessor) FakerProcessorStream.buildStream()

    val jsonSchemaRaw = Source.fromFile(jsonSchemaFilePath).getLines.mkString
    val jsonSchemaValue = parse(jsonSchemaRaw).toOption.get
    val jsonSchema: Schema = Schema.load(jsonSchemaValue)

    val route = handleRejections(myRejectionHandler) {
      concat(
        get {
          path("ready") {
            if (PoolControl.atomicIndex.get() != -1 && receiverStream.state() == State.RUNNING) {
              complete(StatusCodes.OK, "Ready")
            } else {
              complete(StatusCodes.ServiceUnavailable, "NotReady")
            }
          }
        },
        get {
          path("validations" / "schema") {
            complete(StatusCodes.OK, jsonSchemaValue)
          }
        },
        post {
          path("validations") {
            entity(as[Json]) { input =>
              jsonSchema.validate(input) match {
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
      )
    }

    Http().newServerAt("localhost", serverPort).bind(route)

    logger.info(s"Server now online. Please navigate to http://localhost:$serverPort")
  }

  private def waitReply(id: String): Future[String] = {
    val actorFuture: Future[ActorRef[RequestActor.Message]] = system.ask(SpawnProtocol.Spawn(RequestActor(id), name = id, props = Props.empty, _))

    actorFuture.flatMap { actor =>
      val resultFuture = actor ? RequestActor.Wait

      resultFuture
    }
  }
}
