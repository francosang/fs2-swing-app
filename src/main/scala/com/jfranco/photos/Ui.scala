package com.jfranco.photos

import cats.effect.IO

import java.awt.event.*
import javax.swing.*
import scala.concurrent.ExecutionContext
import scala.swing.*

private object AwtEventDispatchEC extends ExecutionContext {
  def execute(r: Runnable): Unit = SwingUtilities.invokeLater(r)

  def reportFailure(t: Throwable): Unit = t.printStackTrace()
}

object Ui {

  def apply(content: Component): IO[Unit] =
    IO.async_[Unit] { cb =>
      val app = new SimpleSwingApplication {
        def top: Frame = new MainFrame {
          peer.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
          peer.addWindowListener(new WindowAdapter() {
            override def windowClosing(e: WindowEvent): Unit = {
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

}
