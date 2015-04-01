package com.cloudera.hue.livy.repl.scala

import com.cloudera.hue.livy.repl.Session
import com.cloudera.hue.livy.repl.scala.interpreter._
import com.cloudera.hue.livy.sessions._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write
import org.json4s.{JValue, _}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object SparkSession {
  def create(): Session = new SparkSession()
}

private class SparkSession extends Session {
  private implicit def executor: ExecutionContext = ExecutionContext.global

  implicit val formats = DefaultFormats

  private var _history = new mutable.ArrayBuffer[JValue]
  private val interpreter = new Interpreter()
  interpreter.start()

  override def kind: Kind = Spark()

  override def state: State = interpreter.state match {
    case Interpreter.NotStarted() => NotStarted()
    case Interpreter.Starting() => Starting()
    case Interpreter.Idle() => Idle()
    case Interpreter.Busy() => Busy()
    case Interpreter.ShuttingDown() => ShuttingDown()
    case Interpreter.ShutDown() => Dead()
  }

  override def history(): Seq[JValue] = _history

  override def history(id: Int): Option[JValue] = synchronized {
    if (id < _history.length) {
      Some(_history(id))
    } else {
      None
    }
  }

  override def execute(code: String): Future[JValue] = {
    Future {
      val content = interpreter.execute(code) match {
        case ExecuteComplete(executeCount, output) =>
          Map(
            "status" -> "ok",
            "execution_count" -> executeCount,
            "data" -> Map(
              "text/plain" -> output
            )
          )
        case ExecuteIncomplete(executeCount, output) =>
          Map(
            "status" -> "error",
            "execution_count" -> executeCount,
            "ename" -> "Error",
            "evalue" -> output
          )
        case ExecuteError(executeCount, output) =>
          Map(
            "status" -> "error",
            "execution_count" -> executeCount,
            "ename" -> "Error",
            "evalue" -> output
          )
      }

      val jsonContent = parse(write(content))

      _history += jsonContent

      jsonContent
    }
  }

  override def close(): Unit = {
    interpreter.shutdown()
  }
}
