package org.fs.mael.backend.http

import java.net.URLDecoder

import scala.collection.immutable.ListMap
import scala.util.parsing.combinator.RegexParsers

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

  /** Checks that cookie key and value contains no illegal characters */
  def validateCookieCharacterSet(k: String, v: String): Unit = {
    requireFriendly(!k.isEmpty, s"Key is empty")
    requireFriendly(k matches CookieParsing.KeyPattern, s"Key ${k} contains illegal characters")
    requireFriendly(v matches CookieParsing.ValPattern, s"Value ${v} contains illegal characters")
  }

  /** Parse a cookie header string (with or without "Cookie:" prefix), yielding key-value pairs  */
  def parseClientCookies(cookieString: String): ListMap[String, String] = {
    CookieParsing.parseAll(CookieParsing.ClientPattern, cookieString) match {
      case CookieParsing.Success(x, _)   => ListMap(x: _*)
      case CookieParsing.Failure(msg, _) => failFriendly(msg)
      case CookieParsing.Error(msg, _)   => failFriendly(msg)
    }
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

  private object CookieParsing extends RegexParsers {
    val KeyPattern = "[a-zA-Z0-9!#$%&'*.^_`|~+-]+"
    val ValPattern = "[a-zA-Z0-9!#$%&'()*./:<=>?@\\[\\]^_`{|}~+-]*"

    val ClientPrefix = """(?i)\QCookie:\E""".r

    val KeyEqualsValue = KeyPattern.r ~ ("=" ~> ValPattern.r) ^^ { case k ~ v => (k, v) }
    val ClientPattern = (ClientPrefix.? ~> KeyEqualsValue ~ (";" ~> KeyEqualsValue).*) ^^ {
      case x ~ y => x +: y
    }
  }
}
