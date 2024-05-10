package hello

import cats.*
import cats.effect.*
import cats.effect.std.Dispatcher
import fs2.*
import cats.implicits.*
import fs2.concurrent.Channel

import java.awt
import java.awt.event.WindowAdapter
import javax.swing.{SwingUtilities, WindowConstants}
import scala.concurrent.ExecutionContext
import scala.swing.event.*
import scala.swing.*

private object AwtEventDispatchEC extends ExecutionContext {
  def execute(r: Runnable): Unit = SwingUtilities.invokeLater(r)
  def reportFailure(t: Throwable): Unit = t.printStackTrace()
}

object App extends IOApp.Simple {

  private case class UIApp(
      dispatch: Action => Unit
  ) extends SimpleSwingApplication {

    val countLabel = new Label("0")

    def onUpdate(state: State): Unit = {
      state match {
        case AppState(counter) => countLabel.text = s"$counter"
      }

      println("onUpdate")
    }

    def top: Frame = new MainFrame {
      title = "Ignored"
      contents = new FlowPanel {
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
      centerOnScreen()
    }
  }

  private def ui(ui: UIApp): IO[Unit] = IO.async_[Unit] { cb =>
    ui.top.peer.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
    ui.top.peer.addWindowListener(new WindowAdapter() {
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

    // Internally posts to AWT-EventQueue thread
    ui.main(Array.empty)
  }

  trait State
  private final case class AppState(counter: Int) extends State

  trait Action
  final case class Increase() extends Action
  final case class Decrease() extends Action

  override def run: IO[Unit] = {
    (
      Channel.unbounded[IO, Action].toResource,
      Ref.of[IO, State](AppState(0)).toResource,
      Dispatcher.sequential[IO](await = true)
    ).tupled.use { case (actions, ref, dispatcher) =>
      val dispatch: Action => Unit = it => {
        dispatcher.unsafeRunAndForget(actions.send(it))
      }

      val uiApp = UIApp(dispatch)

      def increase(state: State): State = {
        state match {
          case AppState(counter) => AppState(counter + 1)
        }
      }

      // This will update a state and send it through the states stream.
      val backgroundProcess =
        actions.stream
          .evalTap { action => IO.println(s"Action $action") }
          .evalMap { action => IO.pure(action).product(ref.get) }
          .collect { case (action, state) =>
            action match {
              case Increase() => increase(state)
            }
          }
          .evalMap { state => ref.set(state) *> IO(uiApp.onUpdate(state)) }
          .compile
          .drain

      backgroundProcess.background.surround {
        IO.println("Starting ui ...") >>
          ui(uiApp) >> // The ui will receive a stream of states.
          IO.println("...ui closed")
      }
    }
  }

}
