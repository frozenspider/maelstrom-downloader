package org.fs.mael.backend.http

import java.io.File
import java.net.URI
import java.net.URL

import scala.collection.immutable.ListMap

import org.fs.mael.backend.http.config.HttpSettings
import org.fs.mael.backend.http.utils.HttpUtils
import org.fs.mael.core.backend.AbstractBackend
import org.fs.mael.core.config.ConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.transfer.TransferManager
import org.fs.mael.core.utils.CoreUtils._

class HttpBackend(
  transferMgr:            TransferManager,
  override val globalCfg: ConfigStore,
  eventMgr:               EventManager
) extends AbstractBackend {
  override val id: String = HttpBackend.Id

  override def init(): Unit = {
    HttpSettings
  }

  override def isSupported(uri: URI): Boolean = {
    try {
      val url = uri.toURL
      (Seq("http", "https") contains url.getProtocol) && !url.getHost.isEmpty
    } catch {
      case ex: Exception => false
    }
  }

  override val downloader = new HttpDownloader(eventMgr, transferMgr)

  override def pageDescriptors = HttpSettings.Local.pageDescriptors

  /**
   * Parse a textual HTTP request, yielding a downloa1dable entry
   */
  def parseHttpRequest(requestString: String, location: File): DownloadEntry = {
    requireFriendly(requestString startsWith "GET ", "Not an HTTP request string")
    val (headers, cookies, userAgentOption) = {
      val headers = HttpUtils.parseHeaders(requestString)
      val headersLC = headers map { case (k, v) => (k.toLowerCase, v) }
      val (headers2, cookies) = headersLC.get("cookie") match {
        case Some(cookieString) =>
          val cookies = HttpUtils.parseClientCookies(cookieString)
          (headers.filterKeys(_.toLowerCase != "cookie"), cookies)
        case None =>
          (headers, ListMap.empty[String, String])
      }
      val (headers3, userAgentOption) = {
        (headers2.filterKeys(_.toLowerCase != "user-agent"), headersLC.get("user-agent"))
      }
      (headers3, cookies, userAgentOption)
    }
    val parsedRequestLineUri = requestString.lines.next match {
      case HttpBackend.RequestPattern(uri) => uri
    }
    val uri = if (parsedRequestLineUri startsWith "/") {
      val host = headers.find(_._1.toLowerCase == "host").get._2
      val url = new URL("http", host, parsedRequestLineUri)
      url.toURI
    } else {
      new URI(parsedRequestLineUri)
    }
    val de = create(uri, location, None, None, "", None)
    de.backendSpecificCfg.set(HttpSettings.UserAgent, userAgentOption)
    de.backendSpecificCfg.set(HttpSettings.Headers, headers)
    de.backendSpecificCfg.set(HttpSettings.Cookies, cookies)
    de
  }

  /**
   * Parse a curl request, yielding a downloadable entry
   */
  def parseCurlRequest(requestString: String, location: File): DownloadEntry = {
    failFriendly("Doh!") // TODO: #40
  }
}

object HttpBackend {
  val Id = "http"

  private val RequestPattern = "GET ([^\\s]+) HTTP/[\\d.]+".r
}
