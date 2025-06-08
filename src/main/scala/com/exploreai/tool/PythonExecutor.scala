package com.exploreai.tool

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.{Task, ZIO, ZLayer}

import java.io.File
import java.io.PrintWriter
import scala.sys.process._
import scala.util.Using

case class PythonCode(code: String)
object PythonCode:
  given JsonDecoder[PythonCode] = DeriveJsonDecoder.gen[PythonCode]

case class ExecutionResult(result: String, error: Option[String] = None)
object ExecutionResult:
  given JsonEncoder[ExecutionResult] = DeriveJsonEncoder.gen[ExecutionResult]

trait PythonExecutor:
  def execute(code: PythonCode): Task[ExecutionResult]

object PythonExecutor:
  def execute(code: PythonCode): ZIO[PythonExecutor, Throwable, ExecutionResult] =
    ZIO.serviceWithZIO[PythonExecutor](_.execute(code))

  val live: ZLayer[Any, Nothing, PythonExecutor] = ZLayer.succeed(new PythonExecutor {
    def execute(pythonCode: PythonCode): Task[ExecutionResult] = ZIO.attempt {
      val tempFile = File.createTempFile("python_script", ".py")
      try {
        Using.resource(new PrintWriter(tempFile)) { writer =>
          writer.write(pythonCode.code)
        }

        val stdout = new StringBuilder
        val stderr = new StringBuilder

        val logger = ProcessLogger(
          (o: String) => stdout.append(o + "\n"),
          (e: String) => stderr.append(e + "\n")
        )

        val exitCode = Seq("python", tempFile.getAbsolutePath).!(logger)

        if (exitCode == 0) {
          ExecutionResult(stdout.toString())
        } else {
          ExecutionResult(stdout.toString(), Some(stderr.toString()))
        }
      } finally {
        tempFile.delete()
      }
    }
  }) 