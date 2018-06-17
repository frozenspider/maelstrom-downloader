package org.fs.mael.backend.http

import java.net.URLDecoder

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
}
