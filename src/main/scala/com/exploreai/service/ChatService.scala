package com.exploreai.service

import com.exploreai.client.OllamaClient
import com.exploreai.core._
import zio._
import zio.http.Client
import zio.stream.ZStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait ChatService {
  def chat(request: ChatRequest): IO[Throwable, String]
  def streamChat(request: ChatRequest): ZStream[Any, Throwable, String]
}

object ChatService {
  val live: ZLayer[OllamaClient with ToolExecutor, Nothing, ChatService] = ZLayer.fromFunction(ChatServiceLive.apply _)
}

case class ChatServiceLive(ollamaClient: OllamaClient, toolExecutor: ToolExecutor) extends ChatService {
  // Model name is now configured internally here
  private val modelName = "llama3.1"
  private val defaultSystemPrompt = "You are a helpful assistant with access to multiple tools. You can use as many as you want and can use them as many times as you want. Provide concise and accurate answers.Don't make up answers. "

  private def prepareInitialMessages(request: ChatRequest): UIO[List[OllamaMessage]] = {
    for {
      currentTime <- zio.Clock.currentDateTime.map(_.toZonedDateTime)
      formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
      timeString = currentTime.format(formatter)
      baseSystemPrompt = request.system.getOrElse(defaultSystemPrompt)
      finalSystemPrompt = s"$baseSystemPrompt\n\nCurrent date and time is $timeString."
    } yield List(
      OllamaMessage(role = "system", content = finalSystemPrompt),
      OllamaMessage(role = "user", content = request.query)
    )
  }

  def chat(request: ChatRequest): IO[Throwable, String] = {
    prepareInitialMessages(request).flatMap { initialMessages =>
      def loop(currentMessages: List[OllamaMessage]): IO[Throwable, String] = {
        val ollamaRequest = OllamaChatRequest(
          model = modelName,
          messages = currentMessages,
          stream = false,
          tools = Some(toolExecutor.supportedTools)
        )
        ollamaClient.chat(ollamaRequest).flatMap { response =>
          response.message.tool_calls match {
            case Some(toolCalls) if toolCalls.nonEmpty =>
              for {
                _           <- ZIO.logInfo(s"Received ${toolCalls.length} tool call(s).")
                toolResults <- ZIO.foreach(toolCalls)(toolExecutor.execute)
                newMessages = currentMessages ++ List(response.message) ++ toolResults
                result      <- loop(newMessages)
              } yield result
            case _ => ZIO.logInfo(s"Received final response from the model with no tool calls.") *>
              ZIO.succeed(response.message.content)
          }
        }
      }
      loop(initialMessages)
    }
  }

  def streamChat(request: ChatRequest): ZStream[Any, Throwable, String] = {
    ZStream.fromZIO(prepareInitialMessages(request)).flatMap { initialMessages =>
      def streamLoop(currentMessages: List[OllamaMessage]): ZStream[Any, Throwable, String] = {
        val ollamaRequest = OllamaChatRequest(
          model = modelName,
          messages = currentMessages,
          stream = true,
          tools = Some(toolExecutor.supportedTools)
        )

        ollamaClient
          .streamChat(ollamaRequest)
          .mapAccumZIO(List.empty[OllamaToolCall]) { (accumulatedToolCalls, response) =>
            if (!response.done) {
              val newToolCalls = accumulatedToolCalls ++ response.message.tool_calls.getOrElse(List.empty)
              ZIO.succeed((newToolCalls, ZStream.succeed(response.message.content)))
            } else {
              val finalToolCalls = accumulatedToolCalls ++ response.message.tool_calls.getOrElse(List.empty)

              if (finalToolCalls.nonEmpty) {
                for {
                  _ <- ZIO.logInfo(s"Stream finished. Executing ${finalToolCalls.length} accumulated tool call(s).")
                  assistantMessage = OllamaMessage(role = "assistant", content = "", tool_calls = Some(finalToolCalls))
                  toolResults <- ZIO.foreach(finalToolCalls)(toolExecutor.execute)
                  _ <- ZIO.logInfo("Tool execution complete. Streaming results back to user.")
                  newMessages = currentMessages ++ List(assistantMessage) ++ toolResults
                } yield {
                  val toolResultStream = ZStream.fromIterable(toolResults).map { toolResult =>
                    val toolName = toolResult.tool_calls.flatMap(_.headOption.map(_.function.name)).getOrElse("unknown_tool")
                    s"[Tool Result: ${toolName}]: ${toolResult.content}\n"
                  }
                  (List.empty, ZStream.succeed(response.message.content) ++ toolResultStream ++ streamLoop(newMessages))
                }
              } else {
                ZIO.logInfo(s"Received final response from the model with no tool calls.") *>
                  ZIO.succeed((List.empty, ZStream.succeed(response.message.content)))
              }
            }
          }
          .flatMap(identity)
      }

      streamLoop(initialMessages)
    }
  }
}
