package com.exploreai.service

import com.exploreai.core.*
import com.exploreai.tool.{ExecutionResult, PythonCode, PythonExecutor, WebSearch}
import com.exploreai.tool.webextractor.WebPageExtractor
import zio.json.{DecoderOps, EncoderOps, JsonDecoder}
import zio.{IO, ZIO, ZLayer}

trait ToolExecutor:
  def execute(toolCall: OllamaToolCall): IO[Throwable, OllamaMessage]
  def supportedTools: List[OllamaTool]

object ToolExecutor:
  val live: ZLayer[WebSearch with WebPageExtractor with PythonExecutor, Nothing, ToolExecutor] =
    ZLayer.fromFunction(ToolExecutorLive.apply)

final case class ToolExecutorLive(webSearch: WebSearch, webPageExtractor: WebPageExtractor, pythonExecutor: PythonExecutor) extends ToolExecutor {
  def execute(toolCall: OllamaToolCall): IO[Throwable, OllamaMessage] =
    toolCall.function.name match
      case "web_search" =>
        toolCall.function.arguments.get("query") match
          case Some(query) =>
            for
              _ <- ZIO.logInfo(s"Executing tool 'web_search' with query: '$query'")
              result <- webSearch.search(query)
            yield OllamaMessage(role = "tool", content = result.toJson, tool_calls = Some(List(toolCall)))
          case None =>
            ZIO.fail(new IllegalArgumentException("Missing 'query' argument for web_search tool."))
      case "webpage_extract" =>
        toolCall.function.arguments.get("url") match
          case Some(url) =>
            for
              _ <- ZIO.logInfo(s"Executing tool 'webpage_extract' with url: '$url'")
              result <- webPageExtractor.extract(url)
            yield OllamaMessage(role = "tool", content = result, tool_calls = Some(List(toolCall)))
          case None =>
            ZIO.fail(new IllegalArgumentException("Missing 'url' argument for webpage_extract tool."))
      case "python_execute" =>
        ZIO.fromEither(toolCall.function.arguments.toJson.fromJson[PythonCode])
          .mapError(e => new IllegalArgumentException(s"Failed to parse arguments for python_execute: $e"))
          .flatMap { args =>
            for {
              _ <- ZIO.logInfo(s"Executing tool 'python_execute' with code snippet : ${args.code}")
              result <- pythonExecutor.execute(args)
            } yield OllamaMessage(role = "tool", content = result.toJson, tool_calls = Some(List(toolCall)))
          }
      case other =>
        ZIO.fail(new NoSuchElementException(s"Unknown tool: $other"))

  val supportedTools: List[OllamaTool] = List(
    OllamaTool(
      `type` = "function",
      function = OllamaFunction(
        name = "web_search",
        description = "Performs a web search to find up-to-date information.",
        parameters = OllamaFunctionParams(
          `type` = "object",
          properties = Map(
            "query" -> OllamaFunctionProperty(`type` = "string", description = "The search query.")
          ),
          required = List("query")
        )
      )
    ),
    OllamaTool(
      `type` = "function",
      function = OllamaFunction(
        name = "webpage_extract",
        description = "Extracts the content from a webpage given its URL.",
        parameters = OllamaFunctionParams(
          `type` = "object",
          properties = Map(
            "url" -> OllamaFunctionProperty(`type` = "string", description = "The URL of the webpage to extract.")
          ),
          required = List("url")
        )
      )
    ),
    OllamaTool(
      `type` = "function",
      function = OllamaFunction(
        name = "python_execute",
        description = "Executes python code and returns the output. The code should be self-contained and not require any external files or user input.",
        parameters = OllamaFunctionParams(
          `type` = "object",
          properties = Map(
            "code" -> OllamaFunctionProperty(`type` = "string", description = "The python code to execute.")
          ),
          required = List("code")
        )
      )
    )
  )
}
