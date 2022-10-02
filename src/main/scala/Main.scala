package claudiostahl

import scala.concurrent.duration._
import scala.concurrent._
import scala.util._
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
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

// domain model
final case class Validation(id: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val validationFormat = jsonFormat1(Validation)
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

  final case class Produce(ref: ActorRef[String]) extends Message

  final case class Reply(value: String) extends Message

  case object Hello extends Message

  case object IdentifyYourself extends Message

  private case object Timeout extends Message

  sealed trait Data

  case object Uninitialized extends Data

  final case class Initialized(target: ActorRef[String]) extends Data

  def apply(id: String):  Behavior[Message] = Behaviors.setup { context =>
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
        case (Produce(ref), Uninitialized) =>
          println("Produce", id)
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

object Main extends JsonSupport {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(MainActor(), "my-system")
  implicit val executionContext: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = 2.seconds

  def main(args: Array[String]): Unit = {

//    var id = "request-id"
//    val key = ServiceKey[RequestActor.Message](id)
//    val maxWaitTime: FiniteDuration = Duration(2, SECONDS)
//    val actorFuture: Future[ActorRef[RequestActor.Message]] = system.ask(SpawnProtocol.Spawn(RequestActor(id), name = "request", props = Props.empty, _))
//    val actor: ActorRef[RequestActor.Message] = Await.result(actorFuture, maxWaitTime)
//
//    val receptionistFulture = Receptionist.get(system).ref.ask(Receptionist.Find(key))
//    receptionistFulture.onComplete {
//      case Success(listing: Receptionist.Listing) =>
//        val instances = listing.serviceInstances(key)
//        val actor2 = instances.iterator.next()
//        actor2 ! RequestActor.Hello
//
//      case Failure(ex) =>
//        println("An error has occurred: " + ex.getMessage)
//    }

//    actor ! RequestActor.Hello
//    actorFuture.onComplete {
//      case Success(actor) => println(actor.toString)
//      case Failure(ex) => println("An error has occurred: " + ex.getMessage)
//    }

    val route =
      path("validations") {
        post {
          entity(as[Validation]) { validation =>
            onComplete(spawnAndProduce(validation.id)) {
              case Success(value) => complete(StatusCodes.OK, value)
              case Failure(ex) => complete(StatusCodes.UnprocessableEntity, ex.getMessage)
            }
          }
        }
      }

    Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server now online. Please navigate to http://localhost:8080")
  }

  private def spawnAndProduce(id: String): Future[String] = {
    val actorFuture: Future[ActorRef[RequestActor.Message]] = system.ask(SpawnProtocol.Spawn(RequestActor(id), name = id, props = Props.empty, _))

    actorFuture.flatMap { actor =>
      println("actor", actor)
      val resultFuture = actor ? RequestActor.Produce
      actor ! RequestActor.Reply("amazing")
      resultFuture
    }
  }
}
