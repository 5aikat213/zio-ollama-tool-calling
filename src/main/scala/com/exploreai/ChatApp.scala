package com.exploreai

import com.exploreai.api.ChatHttpApp
import com.exploreai.client.OllamaClient
import com.exploreai.service.{ ChatService, ToolExecutor}
import com.exploreai.tool.PythonExecutor
import com.exploreai.tool.websearch.tavily.TavilyWebSearch
import com.exploreai.tool.webextractor.WebPageExtractor
import zio._
import zio.http.{Client, Server}
import zio.logging.{consoleLogger, ConsoleLoggerConfig, LogFormat}

object ChatApp extends ZIOAppDefault {

  private val DEFAULT_PORT = 9000

  override val bootstrap: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> consoleLogger(
      ConsoleLoggerConfig.default.copy(
        format = LogFormat.timestamp |-| LogFormat.fiberId |-| LogFormat.level |-| LogFormat.spans |-| LogFormat.line
      )
    )

  private def program: ZIO[Any, Throwable, Unit] =
    Server.serve(ChatHttpApp()).provide(
      Server.defaultWithPort(DEFAULT_PORT),
      ChatService.live,
      OllamaClient.live,
      ToolExecutor.live,
      Client.default,
      TavilyWebSearch.live,
      WebPageExtractor.live,
      PythonExecutor.live
    )

  def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program.catchAll(e => ZIO.logError(e.getMessage))

}
