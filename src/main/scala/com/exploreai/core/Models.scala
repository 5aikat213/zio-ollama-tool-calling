package com.exploreai.core

import zio.json._

// Represents the simplified request body for the /chat and /chat/stream endpoints.
// It now takes a direct query and an optional system prompt.
final case class ChatRequest(query: String, system: Option[String] = None)
object ChatRequest {
  implicit val codec: JsonCodec[ChatRequest] = DeriveJsonCodec.gen
}

// --- Ollama Client Models ---

// Represents a single tool that the model can use.
final case class OllamaTool(
                             `type`: String,
                             function: OllamaFunction
                           )
object OllamaTool {
  implicit val codec: JsonCodec[OllamaTool] = DeriveJsonCodec.gen
}

// Describes the function within a tool.
final case class OllamaFunction(
                                 name: String,
                                 description: String,
                                 parameters: OllamaFunctionParams
                               )
object OllamaFunction {
  implicit val codec: JsonCodec[OllamaFunction] = DeriveJsonCodec.gen
}

// Describes the parameters for a function.
final case class OllamaFunctionParams(
                                       `type`: String,
                                       properties: Map[String, OllamaFunctionProperty],
                                       required: List[String]
                                     )
object OllamaFunctionParams {
  implicit val codec: JsonCodec[OllamaFunctionParams] = DeriveJsonCodec.gen
}

// Describes a single parameter property.
final case class OllamaFunctionProperty(
                                         `type`: String,
                                         description: String
                                       )
object OllamaFunctionProperty {
  implicit val codec: JsonCodec[OllamaFunctionProperty] = DeriveJsonCodec.gen
}

// Represents a message sent to the Ollama API.
final case class OllamaMessage(
                                role: String,
                                content: String,
                                tool_calls: Option[List[OllamaToolCall]] = None // Used for responding with tool results
                              )
object OllamaMessage {
  implicit val codec: JsonCodec[OllamaMessage] = DeriveJsonCodec.gen
}

// The main request body for the Ollama /api/chat endpoint.
final case class OllamaChatRequest(
                                    model: String,
                                    messages: List[OllamaMessage],
                                    stream: Boolean,
                                    tools: Option[List[OllamaTool]] = None
                                  )
object OllamaChatRequest {
  implicit val codec: JsonCodec[OllamaChatRequest] = DeriveJsonCodec.gen
}

// Represents a tool call requested by the model in its response.
final case class OllamaToolCall(
                                 id: Option[String],
                                 `type`: Option[String],
                                 function: OllamaToolCallFunction
                               )
object OllamaToolCall {
  implicit val codec: JsonCodec[OllamaToolCall] = DeriveJsonCodec.gen
}

// Contains the name and arguments of the function to be called.
final case class OllamaToolCallFunction(
                                         name: String,
                                         arguments: Map[String, String]
                                       )
object OllamaToolCallFunction {
  implicit val codec: JsonCodec[OllamaToolCallFunction] = DeriveJsonCodec.gen
}

// Represents a message received from the Ollama API.
// This is used for both streaming and non-streaming responses.
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
  // Manually handle the case where `tool_calls` is in the message
  implicit val codec: JsonCodec[OllamaChatResponse] = DeriveJsonCodec.gen[OllamaChatResponse]
}