package org.fs.mael.backend.http

import java.net.URLDecoder

import scala.collection.immutable.ListMap

import org.fs.mael.core.entry.DownloadEntry

object HttpUtils {
  /**
   * Decodes a string from the RFC 5987 `ext-value` syntax element,
   * such as {{{filename*=UTF-8''%D0%9E.zip}}}
   *
   * (As per {@link https://tools.ietf.org/html/rfc5987})
   */
  def decodeRfc5987ExtValue(s: String): String = {
    val split = s.split("'", 3)
    val (charset, lang, encoded) = (split(0), split(1), split(2))
    charset.toUpperCase match {
      case "UTF-8"      => // NOOP
      case "ISO-8859-1" => // NOOP
      case _ =>
        throw new IllegalArgumentException(s"Charset $charset is neither of the default charsets" +
          ", and extension charsets are reserved by RFC 5987")
    }
    // TODO: What to do with language?
    val decoded = URLDecoder.decode(encoded, charset)
    decoded
  }

  /**
   * Parse a cookie header string (with or without "Set-Cookie: " prefix), yielding a list of key-value pairs
   */
  def parseCookies(cookieString: String): ListMap[String, String] = {
    ???
  }

  /**
   * Parse a textual HTTP request, yielding a downloa1dable entry
   */
  def parseHttpRequest(cookieString: String): DownloadEntry = {
    ??? // TODO: #39
  }

  /**
   * Parse a curl request, yielding a downloadable entry
   */
  def parseCurlRequest(cookieString: String): DownloadEntry = {
    ??? // TODO: #40
  }
}
