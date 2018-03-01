package org.fs.mael.backend.http

import org.apache.http.HttpResponse
import org.apache.http.config.MessageConstraints
import org.apache.http.io.HttpMessageParser
import org.apache.http.io.HttpMessageParserFactory
import org.apache.http.io.SessionInputBuffer
import org.apache.http.message.BasicLineFormatter
import org.apache.http.util.CharArrayBuffer
import org.fs.mael.core.CoreUtils._

class HttpMessageParserProxyFactory(
  delegate:    HttpMessageParserFactory[HttpResponse],
  logResponse: String => Unit
) extends HttpMessageParserFactory[HttpResponse] {

  /** LineFormatter used in DefaultHttpRequestWriterFactory.INSTANCE */
  val lineFormatter = BasicLineFormatter.INSTANCE

  override def create(buffer: SessionInputBuffer, constraints: MessageConstraints): HttpMessageParser[HttpResponse] = {
    new HttpMessageParserProxy(delegate.create(buffer, constraints))
  }

  class HttpMessageParserProxy(delegate: HttpMessageParser[HttpResponse]) extends HttpMessageParser[HttpResponse] {
    override def parse(): HttpResponse = {
      if (Thread.currentThread().isInterrupted)
        throw new InterruptedException

      // Intercepting low-level parsing is inconvenient here, so we
      // go along with reverse-formatting already parsed response
      delegate.parse().withCode { res =>
        val resStrBuilder = new StringBuilder
        val buffer = new CharArrayBuffer(50)
        resStrBuilder ++= res.getStatusLine.toString
        resStrBuilder ++= "\n"
        for (header <- res.getAllHeaders) {
          resStrBuilder ++= lineFormatter.formatHeader(buffer, header).toString
          resStrBuilder ++= "\n"
        }

        logResponse(resStrBuilder.toString.trim)
      }
    }
  }
}
