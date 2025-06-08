package com.exploreai.api

import com.exploreai.core.ChatRequest
import com.exploreai.service.ChatService
import zio._
import zio.http._
import zio.json._
import zio.schema.Schema
import zio.schema.codec.BinaryCodec
import zio.schema.codec.JsonCodec.zioJsonBinaryCodec
import zio.stream.ZStream

object ChatHttpApp {
  def apply(): Routes[ChatService, Response] =
    Routes(
      Method.POST / "chat" -> handler { (req: Request) =>
        for {
          chatReq <- req.body.asString.flatMap(s => ZIO.fromEither(s.fromJson[ChatRequest])).mapError(e => Response.badRequest(s"Invalid request body: $e"))
          service <- ZIO.service[ChatService]
          responseContent <- service.chat(chatReq)
            .map(content => s"""{"response": "$content"}""")
            .catchAll {
              case e: NoSuchElementException => ZIO.succeed(Response.badRequest(s"Error: ${e.getMessage}"))
              case e: IllegalArgumentException => ZIO.succeed(Response.badRequest(s"Error: ${e.getMessage}"))
              case e => ZIO.logError(s"Internal server error: ${e.getMessage}") *> ZIO.succeed(Response.internalServerError(s"An unexpected error occurred: ${e.getMessage}"))
            }
          response <- responseContent match {
            case r: Response => ZIO.succeed(r)
            case s: String => ZIO.succeed(Response.json(s))
          }
        } yield response
      },
      Method.POST / "chat" / "stream" -> handler { (req: Request) =>
        (for {
          chatReq <- req.body.asString
            .flatMap(s => ZIO.fromEither(s.fromJson[ChatRequest]))
            .mapError(e => Response.badRequest(s"Invalid request body: $e"))
          service <- ZIO.service[ChatService]
        } yield {
          val stream = service.streamChat(chatReq)
            .map(chunk => ServerSentEvent(chunk, Some("message")))
            .catchAll(e => ZStream.succeed(ServerSentEvent(s"Stream Error: ${e.getMessage}", Some("error"))))
          Response.fromServerSentEvents(stream)
        }).merge
      }
    )
}
