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
  private val defaultSystemPrompt =
    s"""You are a powerful assistant with access to a variety of tools. Your primary goal is to break down complex problems into a sequence of smaller, manageable tasks.
       |
       |When you receive a request, first, think about the best strategy to solve it. This may involve using one or more tools in succession.
       |
       |1.  **Analyze the Request**: Understand what the user is asking for. Identify the key information needed and the steps required to get it.
       |2.  **Plan Your Actions**: Formulate a plan. If you need to use a tool, decide which one is most appropriate. If the task requires multiple steps, think about the order of operations.
       |3.  **Execute and Observe**: Use a tool. After you get a result, analyze it. The outcome of one tool may inform which tool you use next.
       |4.  **Iterate**: If the first result isn't enough to answer the user's query, continue the process. Use more tools as needed. You can use as many tools as you want, as many times as you want.
       |5.  **Synthesize and Respond**: Once you have all the information you need, formulate a final, concise, and accurate answer for the user. Do not make up answers.
       |""".stripMargin

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
