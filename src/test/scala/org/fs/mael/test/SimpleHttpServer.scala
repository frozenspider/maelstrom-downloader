package org.fs.mael.test

import java.net.InetAddress
import java.net.SocketException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit

import org.apache.http.ConnectionClosedException
import org.apache.http.ExceptionLogger
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.MethodNotSupportedException
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.ContentType
import org.apache.http.impl.bootstrap.HttpServer
import org.apache.http.impl.bootstrap.ServerBootstrap
import org.apache.http.protocol.HttpContext
import org.apache.http.protocol.HttpRequestHandler
import org.apache.http.ssl.SSLContexts
import org.slf4s.Logging

import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

class SimpleHttpServer(
  port:          Int,
  waitTimeoutMs: Int,
  sslContext:    SSLContext,
  onFail:        Exception => Unit
) extends Logging { self =>

  @volatile var reqCounter = 0

  @volatile private var handle: (HttpRequest, HttpResponse) => Unit = _

  val server: HttpServer = createServer()

  private def createServer(): HttpServer = {
    val socketConfig = SocketConfig.custom()
      .setSoTimeout(waitTimeoutMs)
      .setTcpNoDelay(true)
      .build()
    val exceptionLogger = new ExceptionLogger {
      override def log(ex: Exception): Unit =
        ex match {
          case _: ConnectionClosedException => // NOOP
          case _: SocketException           => // NOOP
          case _: SSLHandshakeException     => // NOOP, should be logged and checked by client
          case _                            => onFail(ex)
        }
    }
    ServerBootstrap.bootstrap()
      .setLocalAddress(InetAddress.getByName("localhost"))
      .setListenerPort(port)
      .setServerInfo("Test/1.1")
      .setSocketConfig(socketConfig)
      .setExceptionLogger(exceptionLogger)
      .setSslContext(sslContext)
      .registerHandler("*", new HttpRequestHandler {
        def handle(request: HttpRequest, response: HttpResponse, context: HttpContext): Unit = {
          val method = request.getRequestLine().getMethod().toUpperCase(Locale.ROOT)
          if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
            throw new MethodNotSupportedException(method + " method not supported")
          }
          reqCounter += 1
          self.handle(request, response)
        }
      })
      .create()
  }

  def start(): Unit =
    server.start()

  def shutdown(): Unit = {
    server.shutdown(0, TimeUnit.MILLISECONDS)
  }

  def respondWith(
    handle: (HttpRequest, HttpResponse) => Unit
  ): Unit = {
    reqCounter = 0
    this.handle = handle
  }
}

/**
 * @see http://www.robinhowlett.com/blog/2016/01/05/everything-you-ever-wanted-to-know-about-ssl-but-were-afraid-to-ask/
 * @see https://github.com/robinhowlett/everything-ssl/blob/master/src/test/java/com/robinhowlett/ssl/EverythingSSLTest.java
 */
object SimpleHttpServer {
  lazy val SelfSignedServerSslContext: SSLContext = {
    val keystorePass = "password"
    val keystore: KeyStore = {
      val keystore = KeyStore.getInstance("jks")
      val is = getClass.getResourceAsStream("/cert/self-signed.jks");
      try {
        keystore.load(is, keystorePass.toCharArray)
      } finally {
        is.close()
      }
      keystore
    }
    val keyManagers: Array[KeyManager] = {
      val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      keyManagerFactory.init(keystore, keystorePass.toCharArray)
      keyManagerFactory.getKeyManagers
    }
    val trustManagers: Array[TrustManager] = {
      val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      trustManagerFactory.init(keystore)
      trustManagerFactory.getTrustManagers
    }
    val sslContext = SSLContexts.custom()
      .setProtocol("TLS")
      .build()
    sslContext.init(keyManagers, trustManagers, new SecureRandom());
    sslContext
  }

  def main(args: Array[String]): Unit = {
    val http = new SimpleHttpServer(80, 0, null, throw _)
    val https = new SimpleHttpServer(443, 0, SelfSignedServerSslContext, throw _)
    val servers = Seq(http, https)
    val content = <html><head><title>SimpleHttpServer</title></head><body>Server works!</body></html>.toString
    servers.map(_.respondWith((req, res) => {
      res.setStatusCode(HttpStatus.SC_OK)
      res.setEntity(new ByteArrayEntity(content.getBytes("UTF-8"), ContentType.TEXT_HTML))
    }))
    servers.map(_.start())
    println("Server started")
    scala.io.StdIn.readLine()
    servers.map(_.shutdown())
    println("Server stopped")
    http.shutdown()
  }
}
