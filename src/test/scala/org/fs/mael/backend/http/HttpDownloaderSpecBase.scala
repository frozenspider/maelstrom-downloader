package org.fs.mael.backend.http

import java.io.File
import java.net.SocketException
import java.net.URI
import java.nio.file.Files

import scala.util.Random

import org.apache.http._
import org.apache.http.entity._
import org.fs.mael.core.Status
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.Events
import org.fs.mael.core.event.PriorityEvent
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.test.TestUtils
import org.fs.mael.test.stub.ControlledTransferManager
import org.fs.mael.test.stub.StoringEventManager
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.TestSuite
import org.slf4s.Logging

/** Base trait for specs testing {@link HttpDownloader} */
trait HttpDownloaderSpecBase
  extends BeforeAndAfter { this: TestSuite with BeforeAndAfterAll =>

  val eventMgr = new ControlledEventManager
  val transferMgr = new ControlledTransferManager
  val downloader = new HttpDownloader(eventMgr, transferMgr)

  private val tmpDir = new File(sys.props("java.io.tmpdir"))
  private var tmpFilenames = Seq.empty[String]

  @volatile private var succeeded: Boolean = false
  @volatile var failureOption: Option[Throwable] = None

  val uriPort = 52345
  val server: SimpleHttpServer = new SimpleHttpServer(uriPort)

  /** Change for debugging to set breakpoints */
  private val waitTimeoutMs = 1000 * 9999

  /** Needs to be called manually from `before {}` block */
  def beforeMethod(): Unit = {
    eventMgr.reset()
    transferMgr.reset()
    failureOption = None
    succeeded = false
    tmpFilenames = Seq.empty
  }

  /** Needs to be called manually from `after {}` block */
  def afterMethod(): Unit = {
    tmpFilenames.foreach {
      new File(tmpDir, _).delete()
    }
  }

  override protected def afterAll(): Unit = {
    server.shutdown()
  }

  //
  // Helper methods
  //

  protected def requestTempFilename(): String = {
    val filename = Random.alphanumeric.take(10).mkString + ".tmp"
    tmpFilenames = filename +: tmpFilenames
    filename
  }

  protected def createDownloadEntry(): DownloadEntry = {
    val uri = new URI(s"http://localhost:$uriPort/mySubUrl/qwe?a=b&c=d")
    val filename = requestTempFilename()
    DownloadEntry(
      backendId          = HttpBackend.Id,
      uri                = uri,
      location           = tmpDir,
      filenameOption     = Some(filename),
      checksumOption     = None,
      comment            = "my comment",
      backendSpecificCfg = BackendConfigStore(TestUtils.emptyGlobalCfg(), HttpBackend.SettingsAccessChecker)
    )
  }

  /** Expect downloader to fire status changed to status1, and then - to status2 */
  protected def expectStatusChangeEvents(de: DownloadEntry, status1: Status, status2: Status): Unit = {
    succeeded = false
    var firstStatusAdopted = false
    eventMgr.intercept {
      case Events.StatusChanged(`de`, _) if de.status == status1 && !firstStatusAdopted =>
        firstStatusAdopted = true
      case Events.StatusChanged(`de`, _) if de.status == status1 =>
        failureOption = Some(new IllegalStateException(s"${status1} fired twice!"))
      case Events.StatusChanged(`de`, _) if de.status == status2 && firstStatusAdopted =>
        succeeded = true
      case Events.StatusChanged(`de`, _) =>
        failureOption = Some(new IllegalStateException(s"Unexpected status ${de.status}, log: ${de.downloadLog.mkString("\n", "\n", "")}"))
    }
  }

  protected def getLocalFileOption(de: DownloadEntry): Option[File] = {
    de.filenameOption map (new File(tmpDir, _))
  }

  protected def readLocalFile(de: DownloadEntry): Array[Byte] = {
    Files.readAllBytes(getLocalFileOption(de).get.toPath)
  }

  protected def assertHasLogEntry(de: DownloadEntry, substr: String): Unit = {
    val hasLogEntry = de.downloadLog.exists(_.details.toLowerCase contains substr)
    assert(hasLogEntry, s": '$substr'")
  }

  protected def assertDoesntHaveLogEntry(de: DownloadEntry, substr: String): Unit = {
    val hasLogEntry = de.downloadLog.exists(_.details.toLowerCase contains substr)
    assert(!hasLogEntry, s": '$substr'")
  }

  protected def assertLastLogEntry(de: DownloadEntry, substr: String): Unit = {
    val lastLogEntry = de.downloadLog.last.details.toLowerCase contains substr
    assert(lastLogEntry, s": '$substr'; was ${de.downloadLog.last}")
  }

  /** Used for server to act like a regular HTTP server serving a file, supporting resuming */
  protected def serveContentNormally(content: Array[Byte]) =
    (req: HttpRequest, res: HttpResponse) => {
      val body = if (req.getHeaders(HttpHeaders.RANGE).isEmpty) {
        res.setStatusCode(HttpStatus.SC_OK)
        new ByteArrayEntity(content, ContentType.APPLICATION_OCTET_STREAM)
      } else {
        val rangeHeaders = req.getHeaders(HttpHeaders.RANGE).map(_.getValue)
        assert(rangeHeaders.size === 1)
        assert(rangeHeaders.head.matches("bytes=[0-9]+-[0-9]*"))
        val range: (Int, Option[Int]) = {
          val parts = rangeHeaders.head.drop(6).split("-")
          val left = parts(0).toInt
          val rightOption = if (parts.size == 1) None else Some(parts(1).toInt)
          (left, rightOption)
        }
        val partialContent = range match {
          case (left, Some(right)) => content.drop(left).take(right - left)
          case (left, None)        => content.drop(left)
        }
        res.setStatusCode(HttpStatus.SC_PARTIAL_CONTENT)
        new ByteArrayEntity(partialContent, ContentType.APPLICATION_OCTET_STREAM)
      }
      res.setEntity(body)
    }

  protected object await {
    /** Wait for all expected events to fire (or unexpected to cause failure) and for all download threads to die */
    def firedAndStopped(): Unit = {
      val waitUntilFiredAndStopped = waitUntil(waitTimeoutMs) {
        (succeeded || failureOption.isDefined) && downloader.test_getThreads.isEmpty
      }
      assert(waitUntilFiredAndStopped)
      failureOption foreach (ex => fail(ex))
    }

    /** Wait for X bytes to be read from transfer manager (since last reset) */
    def read(x: Int): Unit = {
      val waitUntilRead = waitUntil(waitTimeoutMs) {
        transferMgr.bytesRead == x || failureOption.isDefined
      }
      assert(waitUntilRead)
      failureOption foreach (ex => fail(ex))
    }

    /** Wait for all download threads to die */
    def stopped(): Unit = {
      val waitUntilStopped = waitUntil(waitTimeoutMs) {
        downloader.test_getThreads.isEmpty
      }
      assert(waitUntilStopped)
    }

    /** Wait for the file associated with download entry to appear on disc */
    def fileAppears(de: DownloadEntry): Unit = {
      val waitUntilFileAppears = waitUntil(waitTimeoutMs) {
        getLocalFileOption(de) map (_.exists) getOrElse false
      }
      assert(waitUntilFileAppears)
    }
  }

  //
  // Helper classes
  //

  protected class ControlledEventManager extends StoringEventManager {
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

  protected class SimpleHttpServer(port: Int) extends Logging { self =>
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
      override def log(ex: Exception): Unit =
        ex match {
          case _: ConnectionClosedException => // NOOP
          case _: SocketException           => // NOOP
          case _                            => failureOption = Some(ex)
        }
    }

    @volatile var reqCounter = 0

    @volatile var handle: (HttpRequest, HttpResponse) => Unit = _

    val server: HttpServer = ServerBootstrap.bootstrap()
      .setListenerPort(port)
      .setServerInfo("Test/1.1")
      .setSocketConfig(socketConfig)
      .setExceptionLogger(exceptionLogger)
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

    server.start()

    def respondWith(
      handle: (HttpRequest, HttpResponse) => Unit
    ): Unit = {
      reqCounter = 0
      this.handle = handle
    }

    def shutdown(): Unit = {
      Option(server) foreach { server =>
        server.stop()
        server.shutdown(0, TimeUnit.SECONDS)
        server.awaitTermination(1, TimeUnit.SECONDS)
      }
    }
  }
}
