package com.jfranco.photos

import com.jfranco.photos.Action.*

import scala.swing.event.*
import scala.swing.{Button, FlowPanel, Label}

case class Counter(dispatch: Action => Unit) extends FlowPanel {
  private val countLabel = new Label("0")

  def onUpdate(state: State): Unit = {
    countLabel.text = state.counter.toString
  }

  contents += countLabel
  contents += new Button {
    text = "Add 1"
    reactions += { case ButtonClicked(_) =>
      dispatch(Increase)
    }
  }
  contents += new Button {
    text = "Subtract 1"
    reactions += { case ButtonClicked(_) =>
      dispatch(Decrease)
    }
  }
}
