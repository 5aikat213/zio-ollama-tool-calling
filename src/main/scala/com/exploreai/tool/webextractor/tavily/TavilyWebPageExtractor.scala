package com.exploreai.tool.webextractor.tavily

import com.exploreai.tool.webextractor.WebPageExtractor
import zio.http.{Body, Client, Header, Headers, MediaType, Method, Request, URL}
import zio.json.*
import zio.{Config, Task, ZIO, ZLayer}

private[tavily] final case class TavilyExtractRequest(
    urls: String,
    extract_depth: String = "basic",
    format: String = "markdown"
)

private[tavily] object TavilyExtractRequest:
  given JsonEncoder[TavilyExtractRequest] = DeriveJsonEncoder.gen[TavilyExtractRequest]

private[tavily] final case class TavilyExtractResult(url: String, raw_content: String)

private[tavily] object TavilyExtractResult:
  given JsonDecoder[TavilyExtractResult] = DeriveJsonDecoder.gen[TavilyExtractResult]

private[tavily] final case class TavilyExtractResponse(results: List[TavilyExtractResult])

private[tavily] object TavilyExtractResponse:
  given JsonDecoder[TavilyExtractResponse] = DeriveJsonDecoder.gen[TavilyExtractResponse]

final private[tavily] class TavilyWebPageExtractor(client: Client, apiKey: String) extends WebPageExtractor:
  private val TavilyApiBaseUrl  = "https://api.tavily.com"
  private val TavilyExtractUrl  = URL.decode(s"$TavilyApiBaseUrl/extract").toOption.get

  override def extract(url: String): Task[String] = ZIO.scoped:
    val requestPayload = TavilyExtractRequest(urls = url)
    val req = Request(
      url = TavilyExtractUrl,
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
      tavilyResponse <- ZIO.fromEither(bodyStr.fromJson[TavilyExtractResponse]).mapError(e => new Exception(e))
      content <- ZIO.fromOption(tavilyResponse.results.headOption.map(_.raw_content))
                   .orElseFail(new Exception(s"No content extracted from url: $url"))
    yield content

object TavilyWebPageExtractor:
  val live: ZLayer[Client, Config.Error, WebPageExtractor] =
    ZLayer {
      for
        client <- ZIO.service[Client]
        apiKey <- ZIO.config(Config.string("TAVILY_API_KEY"))
      yield new TavilyWebPageExtractor(client, apiKey)
    } 