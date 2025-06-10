package com.exploreai.core

case class ModelCapabilities(supportsThinking: Boolean)

object OllamaModel {
  private val modelCapabilities: Map[String, ModelCapabilities] = Map(
    "llama3.1" -> ModelCapabilities(supportsThinking = false),
    "qwen3:8b" -> ModelCapabilities(supportsThinking = true)
  )

  def supportsThinking(modelName: String): Boolean =
    modelCapabilities.get(modelName).exists(_.supportsThinking)
} 