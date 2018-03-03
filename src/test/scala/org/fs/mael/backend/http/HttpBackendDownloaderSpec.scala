package org.fs.mael.backend.http

import java.io.File
import java.net.URI
import java.nio.charset.Charset

import org.apache.http._
import org.apache.http.entity._
import org.fs.mael.core.Status
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.Events
import org.fs.mael.core.event.PriorityEvent
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.test.stub.ControlledTransferManager
import org.fs.mael.test.stub.StoringEventManager
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import scala.util.Random
import java.nio.file.Files

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HttpBackendDownloaderSpec
  extends FunSuite
  with BeforeAndAfter {

  private val eventMgr = new ControlledEventManager
  private val transferMgr = new ControlledTransferManager

  private val downloader = new HttpBackendDownloader(eventMgr, transferMgr)
  private val tmpDir = new File(sys.props("java.io.tmpdir"))
  private var tmpFilenames = Seq.empty[String]

  @volatile private var succeeded: Boolean = false
  @volatile private var failureOption: Option[Exception] = None

  private val port = 52345
  private val server: SimpleHttpServer = new SimpleHttpServer(port)

  before {
    eventMgr.reset()
    transferMgr.reset()
    failureOption = None
    succeeded = false
    tmpFilenames = Seq.empty
  }

  after {
    server.shutdown()
    tmpFilenames.foreach {
      new File(tmpDir, _).delete()
    }
  }

  test("regular download of 5 bytes") {
    val uri = new URI(s"http://localhost:$port/mySubUrl")
    val filename = requestTempFilename()
    val de = DownloadEntry[HttpEntryData](
      backendId           = HttpBackend.Id,
      uri                 = uri,
      location            = tmpDir,
      filenameOption      = Some(filename),
      comment             = "my comment",
      backendSpecificData = new HttpEntryData
    )
    eventMgr.intercept {
      case Events.StatusChanged(de, _) if de.status == Status.Error =>
        failureOption = Some(new IllegalStateException("Unexpected status"))
      case Events.StatusChanged(de, _) if de.status == Status.Complete =>
        succeeded = true
    }
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.start { (req, res) =>
      val target = req.getRequestLine.getUri
      val ct = ContentType.create("text/html", null.asInstanceOf[Charset])
      val body = new ByteArrayEntity(expectedBytes, ct)
      res.setStatusCode(HttpStatus.SC_OK)
      res.setEntity(body)
    }
    transferMgr.start()
    downloader.start(de, 999999)
    waitUntil(() => succeeded || failureOption.isDefined, 100000000)
    failureOption foreach (ex => fail(ex))
    val actualBytes = Files.readAllBytes(new File(tmpDir, filename).toPath)
    assert(actualBytes === expectedBytes)
  }

  private def requestTempFilename(): String = {
    val filename = Random.alphanumeric.take(10).mkString + ".tmp"
    tmpFilenames = filename +: tmpFilenames
    filename
  }

  private class ControlledEventManager extends StoringEventManager {
    private type PF = PartialFunction[PriorityEvent, Unit]
    private var eventInterceptors: Seq[PF] = Seq.empty

    override def fire(event: PriorityEvent): Unit = {
      eventInterceptors.foreach { pf =>
        if (pf.isDefinedAt(event)) {
          pf(event)
        }
      }
      super.fire(event)
    }

    def intercept(pf: PF): Unit = {
      eventInterceptors = eventInterceptors :+ pf
    }

    override def reset(): Unit = {
      eventInterceptors = Seq.empty
      super.reset()
    }
  }

  private class SimpleHttpServer(port: Int) {
    import java.util.Locale
    import java.util.concurrent.TimeUnit
    import org.apache.http.config.SocketConfig
    import org.apache.http.impl.bootstrap._
    import org.apache.http.protocol._

    val socketConfig = SocketConfig.custom()
      // .setSoTimeout(15000)
      .setTcpNoDelay(true)
      .build()

    val exceptionLogger = new ExceptionLogger {
      override def log(ex: Exception): Unit = ex match {
        case _: ConnectionClosedException => // NOOP
        case _                            => failureOption = Some(ex)
      }
    }

    def serverBootstrap = ServerBootstrap.bootstrap()
      .setListenerPort(port)
      .setServerInfo("Test/1.1")
      .setSocketConfig(socketConfig)
      .setExceptionLogger(exceptionLogger)

    var server: HttpServer = _

    def start(
      handle2: (HttpRequest, HttpResponse) => Unit
    ): Unit = {
      server = serverBootstrap.registerHandler("*", new HttpRequestHandler {
        def handle(request: HttpRequest, response: HttpResponse, context: HttpContext): Unit = {
          val method = request.getRequestLine().getMethod().toUpperCase(Locale.ROOT)
          if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
            throw new MethodNotSupportedException(method + " method not supported")
          }
          handle2(request, response)
        }
      }).create()

      server.start()
    }

    def shutdown(): Unit = {
      Option(server) foreach (_.shutdown(0, TimeUnit.SECONDS))
    }
  }
}
