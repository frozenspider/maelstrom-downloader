package org.fs.mael.backend.http.utils

import java.io.ByteArrayInputStream
import java.net.URLDecoder

import scala.collection.immutable.ListMap
import scala.io.Codec
import scala.util.parsing.combinator.RegexParsers

import org.fs.mael.core.utils.CoreUtils._
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.fs.mael.core.utils.CoreUtils

object HttpUtils {
  /**
   * Decodes a string from the RFC 5987 `ext-value` syntax element,
   * such as {{{filename*=UTF-8''%D0%9E.zip}}}
   *
   * (As per {@link https://tools.ietf.org/html/rfc5987})
   */
  def decodeRfc5987ExtValue(s: String): String = {
    val Array(charset, lang, encoded) = s.split("'", 3)
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
   * Ensure validity of the given `SSLConnectionSocketFactory`.
   * This is needed because OpenJDK may have no root certs defined and that will lead to runtime exceptions.
   */
  def validateSslConnSocketFactory(sf: SSLConnectionSocketFactory): Unit = {
    val trustedCertsSet = CoreUtils.getNestedPrivateField(
      sf,
      List("socketfactory", "context", "trustManager", "trustedCerts")
    ).asInstanceOf[java.util.Set[_]]
    requireFriendly(!trustedCertsSet.isEmpty, "Your JVM defines no root CA certificates, SSL validation is impossible!"
      + "\nYou may fix this by manually replacing $JRE_HOME/lib/security/cacerts file with the one from Oracle JRE")
  }

  def validateCookiesCharacterSet(cookiesMap: Map[String, String]): Unit = {
    cookiesMap foreach { case (k, v) => validateCookieCharacterSet(k, v) }
  }

  /** Checks that cookie key and value contains no illegal characters */
  def validateCookieCharacterSet(k: String, v: String): Unit = {
    requireFriendly(!k.isEmpty, s"Key is empty")
    requireFriendly(k matches CookieParsing.KeyPattern, s"Key ${k} contains illegal characters")
    requireFriendly(v matches CookieParsing.ValPattern, s"Value ${v} contains illegal characters")
  }

  def validateHeadersCharacterSet(headersMap: Map[String, String]): Unit = {
    headersMap foreach { case (k, v) => validateHeaderCharacterSet(k, v) }
  }

  /** Checks that header key and value contains no illegal characters */
  def validateHeaderCharacterSet(k: String, v: String): Unit = {
    requireFriendly(!k.isEmpty, s"Key is empty")
    requireFriendly(!v.isEmpty, s"Value is empty")
    requireFriendly(k matches HeaderParsing.KeyPattern, s"Key ${k} contains illegal characters")
    requireFriendly(v matches HeaderParsing.ValPattern, s"Value ${v} contains illegal characters")
  }

  /** Parse a cookie header string (with or without "Cookie:" prefix), yielding key-value pairs  */
  def parseClientCookies(cookieString: String): ListMap[String, String] = {
    CookieParsing.parseAll(CookieParsing.ClientPattern, cookieString) match {
      case CookieParsing.Success(x, _)   => ListMap(x: _*)
      case CookieParsing.Failure(msg, _) => failFriendly(msg)
      case CookieParsing.Error(msg, _)   => failFriendly(msg)
    }
  }

  /** Parse a headers string (with or without first line "GET ..."), yielding key-value pairs  */
  def parseHeaders(headersString: String): ListMap[String, String] = {
    val headersString2 = if (headersString.startsWith("GET ")) {
      headersString.dropWhile(_ != '\n').drop(1)
    } else {
      headersString
    }
    // Mimicking the way Apache HttpClient does the parsing
    import org.apache.http.impl.io._
    import org.apache.http.message._
    val is = new ByteArrayInputStream(headersString2.getBytes(Codec.UTF8.charSet))
    val inputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), headersString.length)
    inputBuffer.bind(is)
    val lineParser = BasicLineParser.INSTANCE
    val headers = AbstractMessageParser.parseHeaders(inputBuffer, 0, 0, lineParser)
    val headersMap = ListMap(headers.map(h => (h.getName -> h.getValue)): _*)
    validateHeadersCharacterSet(headersMap)
    headersMap
  }

  private object CookieParsing extends RegexParsers {
    val KeyPattern = "[a-zA-Z0-9!#$%&'*.^_`|~+-]+"
    val ValPattern = "[a-zA-Z0-9!#$%&'()*./:<=>?@\\[\\]^_`{|}~+-]*"

    val ClientPrefix = """(?i)\QCookie:\E""".r
    val KeyEqualsValue = KeyPattern.r ~ ("=" ~> ValPattern.r) ^^ { case k ~ v => (k, v) }

    val ClientPattern = (ClientPrefix.? ~> KeyEqualsValue ~ (";" ~> KeyEqualsValue).*) ^^ {
      case x ~ xs => x +: xs
    }
  }

  private object HeaderParsing extends RegexParsers {
    val KeyPattern = "[a-zA-Z0-9!#$%&'*.^_`|~+-]+"
    val ValPattern = "[ -~]*" // All ASCII chars, as per RFC 7230
  }
}
