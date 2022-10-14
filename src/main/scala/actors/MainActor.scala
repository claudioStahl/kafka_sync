package sandbox_akka

import akka.actor.typed.{Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors

object MainActor {
  def apply(): Behavior[SpawnProtocol.Command] =
    Behaviors.setup { context =>
      // Start initial tasks
      // context.spawn(...)

      SpawnProtocol()
    }
}
