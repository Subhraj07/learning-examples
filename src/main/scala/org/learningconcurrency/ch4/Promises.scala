package org.learningconcurrency
package ch4






object PromisesCreate extends App {
  import scala.concurrent._
  import ExecutionContext.Implicits.global

  val p = Promise[String]
  val q = Promise[String]

  p.future onSuccess {
    case text => log(s"Promise p succeeded with '$text'")
  }

  p success "kept"
  
  val secondAttempt = p trySuccess "kept again"

  log(s"Second attempt to complete the same promise went well? $secondAttempt")

  q failure new Exception("not kept")

  q.future onFailure {
    case t => log(s"Promise q failed with $t")
  }

}


object PromisesCustomAsync extends App {
  import scala.concurrent._
  import ExecutionContext.Implicits.global
  import scala.util.control.NonFatal

  def myAsync[T](body: =>T): Future[T] = {
    val p = Promise[T]

    global.execute(new Runnable {
      def run() = try {
        val result = body
        p success result
      } catch {
        case NonFatal(e) =>
          p failure e
      }
    })

    p.future
  }

  val future = myAsync {
    "naaa" + "na" * 8 + " Katamary Damacy!"
  }

  future onSuccess {
    case text => log(text)
  }

}


object PromisesAndCallbacks extends App {
  import scala.concurrent._
  import ExecutionContext.Implicits.global
  import org.apache.commons.io.monitor._
  import java.io.File


  def fileCreated(directory: String): Future[String] = {
    val p = Promise[String]

    val fileMonitor = new FileAlterationMonitor(1000)
    val observer = new FileAlterationObserver(directory)
    val listener = new FileAlterationListenerAdaptor {
      override def onFileCreate(file: File) {
        try {
          p.trySuccess(file.getName)
        } finally {
          fileMonitor.stop()
        }
      }
    }
    observer.addListener(listener)
    fileMonitor.addObserver(observer)
    fileMonitor.start()

    p.future
  }

  fileCreated(".") onSuccess {
    case filename => log(s"Detected new file '$filename'")
  }

}


object PromisesAndCustomOperations extends App {
  import scala.concurrent._
  import ExecutionContext.Implicits.global

  implicit class FutureOps[T](val self: Future[T]) {
    def or(that: Future[T]): Future[T] = {
      val p = Promise[T]
      self onSuccess { case x => p trySuccess x }
      that onSuccess { case y => p trySuccess y }
      p.future
    }
  }

  val f = Future { "now" } or Future { "later" }

  f onSuccess {
    case when => log(s"The future is $when")
  }

}


object PromisesAndTimers extends App {
  import java.util._
  import scala.concurrent._
  import ExecutionContext.Implicits.global
  import PromisesAndCustomOperations._

  private val timer = new Timer(true)

  def timeout(millis: Long): Future[Unit] = {
    val p = Promise[Unit]
    timer.schedule(new TimerTask {
      def run() = p success ()
    }, millis)
    p.future
  }

  val f = timeout(1000).map(_ => "timeout!") or Future {
    Thread.sleep(999)
    "work completed!"
  }

  f onSuccess {
    case text => log(text)
  }

}

