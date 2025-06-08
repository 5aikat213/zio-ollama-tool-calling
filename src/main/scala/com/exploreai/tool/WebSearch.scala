package com.exploreai.tool

import zio.json.{DeriveJsonEncoder, JsonEncoder}
import zio.{Task, ZIO}

case class Result(title: String, url: String, content: String, score: Double, raw_content: Option[String])
object Result:
  given JsonEncoder[Result] = DeriveJsonEncoder.gen[Result]

case class SearchResult(results: List[Result])
object SearchResult:
  given JsonEncoder[SearchResult] = DeriveJsonEncoder.gen[SearchResult]

trait WebSearch:
  def search(query: String): Task[SearchResult]

object WebSearch:
  def search(query: String): ZIO[WebSearch, Throwable, SearchResult] =
    ZIO.serviceWithZIO[WebSearch](_.search(query))
