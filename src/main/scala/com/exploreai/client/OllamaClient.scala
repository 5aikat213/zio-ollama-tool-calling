package com.exploreai.client
import com.exploreai.core.{OllamaChatRequest, OllamaChatResponse, OllamaMessage, OllamaModel}
import zio._
import zio.http.{Body, Client, Request, URL}
import zio.json._
import zio.stream.{ZPipeline, ZStream}

trait OllamaClient {
  def chat(request: OllamaChatRequest): IO[Throwable, OllamaChatResponse]
  def streamChat(request: OllamaChatRequest): ZStream[Any, Throwable, OllamaChatResponse]
}

object OllamaClient {
  val live: ZLayer[Client, Nothing, OllamaClient] = ZLayer.fromFunction(OllamaClientLive.apply _)

  def scoped: ZIO[Client, Nothing, OllamaClient] = ZIO.service[Client].map(OllamaClientLive.apply)
}

case class OllamaClientLive(client: Client) extends OllamaClient {
  private val OLLAMA_API_URL = "http://localhost:11434/api/chat"

  private def prepareRequest(request: OllamaChatRequest): OllamaChatRequest = {
    request.copy(think = OllamaModel.supportsThinking(request.model))
  }

  def chat(request: OllamaChatRequest): IO[Throwable, OllamaChatResponse] = {
    val finalRequest = prepareRequest(request)
    ZIO.scoped {
      for {
        _ <- ZIO.logInfo(s"Sending non-streaming request to Ollama model: ${finalRequest.model}")
        responseString <- client.request(Request.post(URL.decode(OLLAMA_API_URL).toOption.get, Body.fromString(finalRequest.copy(stream = false).toJson)))
          .flatMap { res =>
            if (res.status.isSuccess) res.body.asString
            else res.body.asString.flatMap(body => ZIO.fail(new Exception(s"Ollama request failed with status ${res.status}: $body")))
          }
        _ <- ZIO.logDebug(s"Ollama raw response: $responseString")
        ollamaResponse <- ZIO.fromEither(responseString.fromJson[OllamaChatResponse])
          .mapError(e => new Exception(s"Failed to decode Ollama response: $e"))
        _ <- ZIO.foreach(ollamaResponse.message.thinking) { thinkingMsg =>
          ZIO.logInfo(s"Model is thinking: $thinkingMsg")
        }
        _ <- ZIO.logInfo("Received non-streaming response from Ollama.")
      } yield ollamaResponse
    }
  }

  def streamChat(request: OllamaChatRequest): ZStream[Any, Throwable, OllamaChatResponse] = {
    val finalRequest = prepareRequest(request)
    ZStream.fromZIO(ZIO.logInfo(s"Sending streaming request to Ollama model: ${finalRequest.model}")) *>
      ZStream.unwrap(ZIO.scoped {
        client.request(Request.post(URL.decode(OLLAMA_API_URL).toOption.get, Body.fromString(finalRequest.toJson))).map { response =>
          if (response.status.isSuccess) {
            response.body.asStream
              .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines) // Stream of JSON strings
              .mapZIO { line =>
                ZIO.fromEither(line.fromJson[OllamaChatResponse])
                  .mapError(e => new Exception(s"Stream decoding failed: $e on line: $line"))
              }
              .tap { r =>
                ZIO.foreach(r.message.thinking) { thinkingMsg =>
                  ZIO.logInfo(s"Ollama is thinking: $thinkingMsg")
                } *> ZIO.when(r.done)(ZIO.logInfo("Ollama stream finished."))
              }
          } else {
            ZStream.fromZIO(response.body.asString.flatMap(body => ZIO.fail(new Exception(s"Ollama stream request failed with status ${response.status}: $body"))))
          }
        }
      })
  }
}
