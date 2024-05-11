package com.jfranco.photos

sealed trait Action

object Action {
  object Increase extends Action
  object Decrease extends Action
}
