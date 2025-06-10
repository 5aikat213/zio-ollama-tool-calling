package com.exploreai.core

import zio.json._

final case class ChatRequest(query: String, system: Option[String] = None)
object ChatRequest {
  implicit val codec: JsonCodec[ChatRequest] = DeriveJsonCodec.gen
}

final case class OllamaTool(
                             `type`: String,
                             function: OllamaFunction
                           )
object OllamaTool {
  implicit val codec: JsonCodec[OllamaTool] = DeriveJsonCodec.gen
}

final case class OllamaFunction(
                                 name: String,
                                 description: String,
                                 parameters: OllamaFunctionParams
                               )
object OllamaFunction {
  implicit val codec: JsonCodec[OllamaFunction] = DeriveJsonCodec.gen
}

final case class OllamaFunctionParams(
                                       `type`: String,
                                       properties: Map[String, OllamaFunctionProperty],
                                       required: List[String]
                                     )
object OllamaFunctionParams {
  implicit val codec: JsonCodec[OllamaFunctionParams] = DeriveJsonCodec.gen
}

final case class OllamaFunctionProperty(
                                         `type`: String,
                                         description: String
                                       )
object OllamaFunctionProperty {
  implicit val codec: JsonCodec[OllamaFunctionProperty] = DeriveJsonCodec.gen
}


final case class OllamaMessage(
                                role: String,
                                content: String,
                                thinking: Option[String] = None,
                                tool_calls: Option[List[OllamaToolCall]] = None // Used for responding with tool results
                              )
object OllamaMessage {
  implicit val codec: JsonCodec[OllamaMessage] = DeriveJsonCodec.gen
}

final case class OllamaChatRequest(
                                    model: String,
                                    messages: List[OllamaMessage],
                                    stream: Boolean,
                                    tools: Option[List[OllamaTool]] = None,
                                    format: Option[String] = None,
                                    think: Boolean = false
                                  )
object OllamaChatRequest {
  implicit val codec: JsonCodec[OllamaChatRequest] = DeriveJsonCodec.gen
}

final case class OllamaToolCall(
                                 id: Option[String],
                                 `type`: Option[String],
                                 function: OllamaToolCallFunction
                               )
object OllamaToolCall {
  implicit val codec: JsonCodec[OllamaToolCall] = DeriveJsonCodec.gen
}

final case class OllamaToolCallFunction(
                                         name: String,
                                         arguments: Map[String, String]
                                       )
object OllamaToolCallFunction {
  implicit val codec: JsonCodec[OllamaToolCallFunction] = DeriveJsonCodec.gen
}

final case class OllamaChatResponse(
                                     model: String,
                                     created_at: String,
                                     message: OllamaMessage,
                                     done: Boolean,
                                     tool_calls: Option[List[OllamaToolCall]] = None, // For non-streaming tool calls
                                     total_duration: Option[Long] = None,
                                     load_duration: Option[Long] = None,
                                     prompt_eval_count: Option[Int] = None,
                                     eval_count: Option[Int] = None
                                   )
object OllamaChatResponse {
  implicit val codec: JsonCodec[OllamaChatResponse] = DeriveJsonCodec.gen[OllamaChatResponse]
}