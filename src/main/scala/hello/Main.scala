package hello

import cats.*
import cats.effect.*

import javax.swing.SwingUtilities
import scala.concurrent.ExecutionContext
import scala.swing.{MainFrame, *}
import scala.swing.event.*

private object AwtEventDispatchEC extends ExecutionContext {
  def execute(r: Runnable): Unit = SwingUtilities.invokeLater(r)
  def reportFailure(t: Throwable): Unit = t.printStackTrace()
}

object App extends IOApp {

  private def ui(args: List[String]): IO[Unit] = IO
    .async_ { cb =>
      // On io-compute

      val app = new SimpleSwingApplication {
        // Still on io-compute

        def top: Frame = new MainFrame {
          // Finally on AWT-EventQueue

          Thread.setDefaultUncaughtExceptionHandler(
            new Thread.UncaughtExceptionHandler() {
              override def uncaughtException(t: Thread, e: Throwable): Unit = {
                cb(Left(e))
              }
            }
          )

          title = "Ignore Window"
          contents = new FlowPanel {
            contents += new Button {
              text = "Error!"
              reactions += { case event.ButtonClicked(_) =>
                println("Button clicked")
                throw new RuntimeException("Boom!")
              }
            }
            contents += new Button {
              text = "Click me"
              reactions += { case event.ButtonClicked(_) =>
                println("Button clicked")
              }
            }
          }
          centerOnScreen()

          override def closeOperation(): Unit = {
            // On AWT-EventQueue
            cb(Right(()))
            super.closeOperation()
          }
        }
      }

      // Internally posts to AWT-EventQueue thread
      app.main(args.toArray)
    }

  override def run(args: List[String]): IO[ExitCode] = {
    IO(println(s"Starting: ${Thread.currentThread().getName}")) *>
      ui(
        args
      ) /* when used with .evalOn(AwtEventDispatchEC) the process hangs after closing ui */ *>
      IO(println(s"Closing: ${Thread.currentThread().getName}"))
        .as(ExitCode.Success)
  }

}
