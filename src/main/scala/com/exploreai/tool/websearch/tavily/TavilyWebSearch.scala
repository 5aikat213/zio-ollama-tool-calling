package com.exploreai.tool.websearch.tavily

import com.exploreai.tool.{Result, SearchResult, WebSearch}
import zio.http.{Body, Client, Header, Headers, MediaType, Method, Request, URL}
import zio.json.*
import zio.{Config, Task, ZIO, ZLayer}

private[tavily] final case class TavilySearchRequest(
    query: String,
    topic: String = "general",
    search_depth: String = "basic",
    max_results: Int = 7,
    include_answer: Boolean = false,
    include_raw_content: Boolean = false,
    include_images: Boolean = false
)

private[tavily] object TavilySearchRequest:
  given JsonEncoder[TavilySearchRequest] = DeriveJsonEncoder.gen[TavilySearchRequest]

private[tavily] final case class TavilyResult(title: String, url: String, content: String, score: Double, raw_content: Option[String])

private[tavily] object TavilyResult:
  given JsonDecoder[TavilyResult] = DeriveJsonDecoder.gen[TavilyResult]

private[tavily] final case class TavilySearchResponse(results: List[TavilyResult])

private[tavily] object TavilySearchResponse:
  given JsonDecoder[TavilySearchResponse] = DeriveJsonDecoder.gen[TavilySearchResponse]

final private[tavily] class TavilyWebSearch(client: Client, apiKey: String) extends WebSearch:
  private val TavilyApiBaseUrl = "https://api.tavily.com"
  private val TavilySearchUrl  = URL.decode(s"$TavilyApiBaseUrl/search").toOption.get

  override def search(query: String): Task[SearchResult] = ZIO.scoped {
    val requestPayload = TavilySearchRequest(query = query)
    val req = Request(
      url = TavilySearchUrl,
      method = Method.POST,
      headers = Headers(
        Header.ContentType(MediaType.application.json),
        Header.Authorization.Bearer(apiKey)
      ),
      body = Body.fromString(requestPayload.toJson)
    )
    for
      res <- client.request(req)
      bodyStr <- res.body.asString
      tavilyResponse <- ZIO.fromEither(bodyStr.fromJson[TavilySearchResponse]).mapError(e => new Exception(e))
      searchResult = SearchResult(
        results = tavilyResponse.results.map(r => Result(r.title, r.url, r.content, r.score, r.raw_content))
      )
      _ <- ZIO.logInfo(s"Received ${searchResult.results.length} results from web search.")
    yield searchResult
  }

object TavilyWebSearch:
  val live: ZLayer[Client, Config.Error, WebSearch] =
    ZLayer {
      for
        client <- ZIO.service[Client]
        apiKey <- ZIO.config(Config.string("TAVILY_API_KEY"))
      yield new TavilyWebSearch(client, apiKey)
    } 