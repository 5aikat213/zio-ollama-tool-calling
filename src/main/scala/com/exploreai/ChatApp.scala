package com.exploreai

import com.exploreai.api.ChatHttpApp
import com.exploreai.client.OllamaClient
import com.exploreai.service.{ChatService, ToolExecutor}
import com.exploreai.tool.PythonExecutor
import com.exploreai.tool.websearch.tavily.TavilyWebSearch
import com.exploreai.tool.webextractor.WebPageExtractor
import zio.*
import zio.http.netty.NettyConfig
import zio.http.{Client, DnsResolver, Server}
import zio.logging.{ConsoleLoggerConfig, LogFormat, consoleLogger}

object ChatApp extends ZIOAppDefault {

  private val DEFAULT_PORT = 9000

  override val bootstrap: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> consoleLogger(
      ConsoleLoggerConfig.default.copy(
        format = LogFormat.timestamp |-| LogFormat.fiberId |-| LogFormat.level |-| LogFormat.spans |-| LogFormat.line
      )
    )

  private val configLayer = ZLayer.succeed(
    Client.Config.default
      .connectionTimeout(Duration.fromSeconds(300))
      .idleTimeout(Duration.fromSeconds(300))
  )

  private val clientLayer = (ZLayer.succeed(NettyConfig.default) ++ DnsResolver.default ++ configLayer) >>> Client.live

  private def program: ZIO[Any, Throwable, Unit] =
    Server.serve(ChatHttpApp()).provide(
    Server.defaultWithPort(DEFAULT_PORT),
    ChatService.live,
    OllamaClient.live,
    ToolExecutor.live,
    clientLayer,
    TavilyWebSearch.live,
    WebPageExtractor.live,
    PythonExecutor.live
  )

  def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program.catchAll(e => ZIO.logError(e.getMessage))

}
