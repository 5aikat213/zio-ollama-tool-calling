package com.exploreai.client
import com.exploreai.core.{OllamaChatRequest, OllamaChatResponse}
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

  def chat(request: OllamaChatRequest): IO[Throwable, OllamaChatResponse] = ZIO.scoped {
    for {
      _ <- ZIO.logInfo(s"Sending non-streaming request to Ollama model: ${request.model}")
      response <- client.request(Request.post(URL.decode(OLLAMA_API_URL).toOption.get, Body.fromString(request.toJson)))
        .flatMap { res =>
          if (res.status.isSuccess) res.body.asString
          else res.body.asString.flatMap(body => ZIO.fail(new Exception(s"Ollama request failed with status ${res.status}: $body")))
        }
      ollamaResponse <- ZIO.fromEither(response.fromJson[OllamaChatResponse])
        .mapError(e => new Exception(s"Failed to decode Ollama response: $e"))
      _ <- ZIO.logInfo("Received non-streaming response from Ollama.")
    } yield ollamaResponse
  }

  def streamChat(request: OllamaChatRequest): ZStream[Any, Throwable, OllamaChatResponse] = {
    ZStream.fromZIO(ZIO.logInfo(s"Sending streaming request to Ollama model: ${request.model}")) *>
      ZStream.unwrap(ZIO.scoped {
        client.request(Request.post(URL.decode(OLLAMA_API_URL).toOption.get, Body.fromString(request.toJson))).map { response =>
          if (response.status.isSuccess) {
            response.body.asStream
              .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines) // Stream of JSON strings
              .mapZIO { line =>
                ZIO.fromEither(line.fromJson[OllamaChatResponse])
                  .mapError(e => new Exception(s"Stream decoding failed: $e on line: $line"))
              }
              .tap(r => if (r.done) ZIO.logInfo("Ollama stream finished.") else ZIO.unit)
          } else {
            ZStream.fromZIO(response.body.asString.flatMap(body => ZIO.fail(new Exception(s"Ollama stream request failed with status ${response.status}: $body"))))
          }
        }
      })
  }
}
