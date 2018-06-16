package org.fs.mael.backend.http

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder

import scala.io.Codec
import scala.util.parsing.combinator.RegexParsers

object HttpUtils {
  object Parser extends RegexParsers {
    override val skipWhitespace = false
    val PercentEncodedCode: Parser[Byte] = (literal("%") ~> s"[0-9a-zA-Z]{2}".r) ^^ { s =>
      (Integer.parseInt(s, 16) & 0xff).toByte
    }
    def PercentEncodedChar(dirtyDecoder: CharsetDecoder): Parser[String] = PercentEncodedCode ^^ { code =>
      val decoder = dirtyDecoder.reset()
      val bb = ByteBuffer.allocate(1)
      bb.put(code)
      val cb = CharBuffer.allocate(bb.limit * decoder.maxCharsPerByte.toInt)
//      val cb = decoder.decode(bb)
      val coderResult = decoder.decode(bb, cb, true)
      val coderResult2 = decoder.flush(cb)
      if (coderResult.isError()) {
        coderResult.throwException()
        throw null
      } else {
        cb.toString()
      }
    }
    val NonPercentChar: Parser[String] = "[^%]".r

    def Rfc5987String(decoder: CharsetDecoder): Parser[String] =
      (PercentEncodedChar(decoder) | NonPercentChar).+ ^^ { _.mkString }
  }

  /**
   * Decodes a string from the RFC 5987 `ext-value` syntax element
   *
   * As per https://tools.ietf.org/html/rfc5987
   */
  def decodeRfc5987ExtValue(s: String): String = {
    val split = s.split("'", 3)
    val (charset, lang, encoded) = (split(0), split(1), split(2))
    val codec = charset.toUpperCase match {
      case "UTF-8"      => Codec.UTF8
      case "ISO-8859-1" => Codec.ISO8859
      case _ =>
        throw new IllegalArgumentException(s"Charset $charset is neither of the default charsets" +
          ", and extension charsets are reserved by RFC 5987")
    }
    // TODO: Lang?
    import Parser._
    val result = parseAll(Rfc5987String(codec.decoder), new java.io.StringReader(s))
    result match {
      case Success(r, _)       => r
      case f @ NoSuccess(m, _) => throw new IllegalArgumentException("Failed to decode RFC 5987 string: " + m)
    }
  }
}
