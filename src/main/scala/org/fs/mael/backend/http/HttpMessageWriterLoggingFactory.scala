package org.fs.mael.backend.http

import org.apache.http.HttpRequest
import org.apache.http.io.HttpMessageWriter
import org.apache.http.io.HttpMessageWriterFactory
import org.apache.http.io.SessionOutputBuffer
import org.apache.http.message.BasicLineFormatter
import org.apache.http.util.CharArrayBuffer

class HttpMessageWriterLoggingFactory(
  delegate:   HttpMessageWriterFactory[HttpRequest],
  logRequest: String => Unit
) extends HttpMessageWriterFactory[HttpRequest] {

  /** LineFormatter used in DefaultHttpRequestWriterFactory.INSTANCE */
  val lineFormatter = BasicLineFormatter.INSTANCE

  override def create(buffer: SessionOutputBuffer): HttpMessageWriter[HttpRequest] = {
    new HttpMessageWriterProxy(delegate.create(buffer))
  }

  class HttpMessageWriterProxy(delegate: HttpMessageWriter[HttpRequest]) extends HttpMessageWriter[HttpRequest] {
    override def write(message: HttpRequest): Unit = {
      if (Thread.currentThread().isInterrupted)
        throw new InterruptedException

      delegate.write(message)

      // Formatting code taken from AbstractMessageWriter
      val reqStrBuilder = new StringBuilder
      val buffer = new CharArrayBuffer(50)
      val reqLine = lineFormatter.formatRequestLine(buffer, message.getRequestLine)
      reqStrBuilder ++= buffer.toString
      reqStrBuilder ++= "\n"
      for (header <- message.getAllHeaders) {
        val reqHeader = lineFormatter.formatHeader(buffer, header)
        reqStrBuilder ++= reqHeader.toString
        reqStrBuilder ++= "\n"
      }

      logRequest(reqStrBuilder.toString.trim)
    }
  }
}
