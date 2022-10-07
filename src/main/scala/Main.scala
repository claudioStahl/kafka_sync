package claudiostahl

import java.util
import java.time.Duration
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

object Main extends JsonSupport {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(MainActor(), "my-system")
  implicit val executionContext: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = 2.seconds

  def main(args: Array[String]): Unit = {
    val rand = new scala.util.Random

    val host = "node" + rand.nextInt().toString
    val topic = "validation_input"
    val producer = Producer.buildProducer()

    PoolControlStream.buildStream(host)
    ReceiverStream.buildStream(host)
    ProcessorStream.buildStream()

    Producer.requestPoolControl(producer, host)

    val route =
      path("validations") {
        post {
          entity(as[ValidationInput]) { input =>
            if (PoolControl.index != -1) {
              Producer.produce(producer, host, topic, input)

              onComplete(waitReply(input.id)) {
                case Success(value) => complete(StatusCodes.OK, value)
                case Failure(ex) => complete(StatusCodes.UnprocessableEntity, ex.getMessage)
              }
            } else {
              complete(StatusCodes.UnprocessableEntity, "not_ready")
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
//      actor ! RequestActor.Reply("amazing")
      resultFuture
    }
  }
}
