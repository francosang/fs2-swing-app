package com.jfranco.photos

import cats.*
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.implicits.*
import com.jfranco.photos.*
import fs2.*
import fs2.concurrent.Channel

object Main extends IOApp.Simple {

  override def run: IO[Unit] = {
    (
      Channel.unbounded[IO, Action].toResource,
      Ref.of[IO, State](State(0)).toResource,
      Dispatcher.sequential[IO](await = true)
    ).tupled.use { case (actions, ref, dispatcher) =>
      val dispatch: Action => Unit = it => {
        dispatcher.unsafeRunAndForget(actions.send(it))
      }

      val counter = Counter(dispatch)

      actions.stream
        .map { action => Handlers(_, action) }
        .evalMap(handler =>
          ref.get
            .flatMap(it => handler(it))
            .flatMap(state =>
              ref.set(state) *> IO
                .pure(state)
                .flatTap(state =>
                  IO(counter.onUpdate(state)).evalOn(AwtEventDispatchEC)
                )
            )
        )
        .compile
        .drain
        .background
        .surround {
          Ui(counter)
        }
    }
  }

}
