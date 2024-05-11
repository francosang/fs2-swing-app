package com.jfranco.photos

import cats.effect.IO
import com.jfranco.photos.Action.{Decrease, Increase}

abstract class ActionHandler[A] {
  def handle(state: State, action: A): IO[State]
}

object IncreaseHandler extends ActionHandler[Action] {
  override def handle(state: State, action: Action): IO[State] = IO.pure {
    state.copy(counter = state.counter + 1)
  }
}

object DecreaseHandler extends ActionHandler[Action] {
  override def handle(state: State, action: Action): IO[State] = IO.pure {
    state.copy(counter = state.counter - 1)
  }
}

object Handlers {
  def apply(state: State, action: Action): IO[State] = {
    action match
      case Action.Increase => IncreaseHandler.handle(state, action)
      case Action.Decrease => DecreaseHandler.handle(state, action)
  }
}
