package sandbox_akka

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors

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
