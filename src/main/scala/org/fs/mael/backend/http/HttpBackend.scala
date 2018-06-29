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
  def parsePlaintextRequest(requestString: String, location: File): DownloadEntry = {
    requireFriendly(requestString startsWith "GET ", "Not an HTTP request string")
    val parsedRequestLineUri = requestString.lines.next match {
      case HttpBackend.RequestPattern(uri) => uri
    }
    val headers = HttpUtils.parseHeaders(requestString)
    val uri = if (parsedRequestLineUri startsWith "/") {
      val host = headers.find(_._1.toLowerCase == "host").get._2
      val url = new URL("http", host, parsedRequestLineUri)
      url.toURI
    } else {
      new URI(parsedRequestLineUri)
    }
    createFrom(uri, headers, location)
  }

  /**
   * Parse a curl request, yielding a downloadable entry
   */
  def parseCurlRequest(requestString: String, location: File): DownloadEntry = {
    val (uriString, options) = {
      import HttpBackend.CurlParsing._
      parseAll(Pattern, requestString) match {
        case Success((uri, options), _) => (uri, options)
        case f @ Failure(msg, _) =>
          println(f); failFriendly(msg)
        case e @ Error(msg, _) => println(e); failFriendly(msg)
      }
    }
    val uri = new URI(uriString)
    val headersString = options collect {
      case (o, args) if o == "H" =>
        requireFriendly(args.size == 1, "Malformed CLI string")
        args.head
    } mkString "\n"
    val headers = HttpUtils.parseHeaders(headersString)
    createFrom(uri, headers, location)
  }

  private def createFrom(uri: URI, _headers: Map[String, String], location: File): DownloadEntry = {
    val (headers, cookies, userAgentOption) = {
      val headersLC = _headers map { case (k, v) => (k.toLowerCase, v) }
      val (headers2, cookies) = headersLC.get("cookie") match {
        case Some(cookieString) =>
          val cookies = HttpUtils.parseClientCookies(cookieString)
          (_headers.filterKeys(_.toLowerCase != "cookie"), cookies)
        case None =>
          (_headers, ListMap.empty[String, String])
      }
      val (headers3, userAgentOption) = {
        (headers2.filterKeys(_.toLowerCase != "user-agent"), headersLC.get("user-agent"))
      }
      (headers3, cookies, userAgentOption)
    }
    val de = create(uri, location, None, None, "", None)
    de.backendSpecificCfg.set(HttpSettings.UserAgent, userAgentOption)
    de.backendSpecificCfg.set(HttpSettings.Headers, headers)
    de.backendSpecificCfg.set(HttpSettings.Cookies, cookies)
    de
  }
}

object HttpBackend {
  val Id = "http"

  private val RequestPattern = "GET ([^\\s]+) HTTP/[\\d.]+".r

  private object CurlParsing extends scala.util.parsing.combinator.RegexParsers {
    override def skipWhitespace = false

    val S = "\\s+".r
    val Q = "(\"|')"
    val OptQuote = (Q + "?").r
    def optQuoted[A](p: Parser[A]): Parser[A] = OptQuote ~> p <~ OptQuote

    val CurlPrefix = "curl(\\..+)?".r ^^^ {}

    val UnquotedString = "[^'\"\\s-]+".r
    val SingleQuotedString = "'((\\\\')|[^\'])*'".r ^^ (s => s substring (1, s.length - 1) replace ("\\'", "'"))
    val DoubleQuotedString = "\"((\\\\\")|(\"[\\S]\")|[^\"])*\"".r ^^ (s => {
      val trimmed = s substring (1, s.length - 1)
      val unescapedQuotedChars = {
        // Tricky stuff: replace "x" with x without considering escaped quotes
        val escapedCharIndices = trimmed.sliding(3).zipWithIndex.toList.foldLeft(Seq.empty[Int]) {
          case (acc, (s, i)) if (s startsWith "\"") && (s endsWith "\"") && (trimmed.charAt(i - 1) != '\\') && (acc.isEmpty || (i - acc.head >= 3)) =>
            i +: acc
          case (acc, _) => acc
        }
        val sb = new StringBuffer(trimmed)
        escapedCharIndices foreach { i =>
          sb replace (i, i + 3, trimmed.substring(i + 1, i + 2))
        }
        sb.toString
      }
      val unescapedQuotes = unescapedQuotedChars replace ("\\\"", "\"")
      unescapedQuotes
    })
    val String = SingleQuotedString | DoubleQuotedString | UnquotedString

    val Url = String

    val ShortOption = ("-" + UnquotedString.regex).r ~ (S ~> String).* ^^ { case (o ~ args) => (o.drop(1), args) }
    val LongOption = ("--" + UnquotedString.regex).r ~ ("=" ~> String).? ^^ { case (o ~ argOpt) => (o.drop(2), argOpt.toSeq) }
    val Option = ShortOption | LongOption

    val Pattern = CurlPrefix ~> S ~> Url ~ (S ~> Option).* ^^ { case (url ~ options) => (url, options) }
  }
}
