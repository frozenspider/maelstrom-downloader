package org.fs.mael.backend.http

import java.net.URLDecoder

import scala.collection.immutable.ListMap

import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.utils.CoreUtils._

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

  private val CookieKeyPattern = "[a-zA-Z0-9!#$%&'*.^_`|~+-]+"
  private val CookieValPattern = "[a-zA-Z0-9!#$%&'()*./:<=>?@\\[\\]^_`{|}~+-]*"

  /** Checks that cookie key and value contains no illegal characters */
  def validateCookieCharacterSet(k: String, v: String): Unit = {
    requireFriendly(!k.isEmpty, s"Key is empty")
    requireFriendly(k matches CookieKeyPattern, s"Key ${k} contains illegal characters")
    requireFriendly(v matches CookieValPattern, s"Value ${v} contains illegal characters")
  }

  /**
   * Parse a cookie header string (with or without "Cookie:"/"Set-Cookie: " prefix),
   * yielding key-value pairs
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
