package hello

import cats.*
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.implicits.*
import fs2.*
import fs2.concurrent.Channel

import java.awt
import java.awt.event.WindowAdapter
import javax.swing.{SwingUtilities, WindowConstants}
import scala.concurrent.ExecutionContext
import scala.swing.*
import scala.swing.event.*

private object AwtEventDispatchEC extends ExecutionContext {
  def execute(r: Runnable): Unit = SwingUtilities.invokeLater(r)
  def reportFailure(t: Throwable): Unit = t.printStackTrace()
}

object App extends IOApp.Simple {

  case class Counter(dispatch: Action => Unit) extends FlowPanel {
    val countLabel = new Label("0")

    def onUpdate(state: State): Unit = {
      countLabel.text = state.counter.toString
    }

    contents += countLabel
    contents += new Button {
      text = "Add 1"
      reactions += { case event.ButtonClicked(_) =>
        dispatch(Increase())
      }
    }
    contents += new Button {
      text = "Subtract 1"
      reactions += { case event.ButtonClicked(_) =>
        dispatch(Decrease())
      }
    }
  }

  private def ui(content: Counter): IO[Unit] = IO.async_[Unit] { cb =>
    val app = new SimpleSwingApplication {
      def top: Frame = new MainFrame {
        peer.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
        peer.addWindowListener(new WindowAdapter() {
          override def windowClosing(e: awt.event.WindowEvent): Unit = {
            println("Window closing.")
            cb(Right(()))
          }
        })

        Thread.setDefaultUncaughtExceptionHandler(
          new Thread.UncaughtExceptionHandler() {
            override def uncaughtException(t: Thread, e: Throwable): Unit = {
              cb(Left(e))
            }
          }
        )

        title = "Ignored"
        contents = content
        centerOnScreen()
      }
    }

    // Internally posts to AWT-EventQueue thread
    app.main(Array.empty)
  }

  final case class State(counter: Int)

  trait Action
  final case class Increase() extends Action
  final case class Decrease() extends Action

  def increase(state: State): State = state.copy(counter = state.counter + 1)

  def decrease(state: State): State = state.copy(counter = state.counter - 1)

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
        .evalTap { action => IO.println(s"Action $action") }
        .collect {
          case Increase() => increase(_)
          case Decrease() => decrease(_)
        }
        .evalMap(handler =>
          ref
            .updateAndGet(handler)
            .flatMap(state => IO(counter.onUpdate(state)))
        )
        .compile
        .drain
        .background
        .surround {
          ui(counter)
        }
    }
  }

}
