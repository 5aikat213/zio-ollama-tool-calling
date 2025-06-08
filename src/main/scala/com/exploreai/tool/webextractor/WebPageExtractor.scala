package com.exploreai.tool.webextractor

import com.exploreai.tool.webextractor.tavily.TavilyWebPageExtractor
import zio.http.Client
import zio.{Config, Task, ZIO, ZLayer}

trait WebPageExtractor:
  def extract(url: String): Task[String]

object WebPageExtractor:
  def extract(url: String): ZIO[WebPageExtractor, Throwable, String] =
    ZIO.serviceWithZIO[WebPageExtractor](_.extract(url))

  val live: ZLayer[Client, Config.Error, WebPageExtractor] = TavilyWebPageExtractor.live 