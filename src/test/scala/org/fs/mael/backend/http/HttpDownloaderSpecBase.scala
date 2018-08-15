package org.fs.mael.backend.http

import java.io.File
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
import org.fs.mael.test.SimpleHttpServer
import org.fs.mael.test.TestUtils
import org.fs.mael.test.stub.ControlledTransferManager
import org.fs.mael.test.stub.StoringEventManager
import org.scalatest.BeforeAndAfter
import org.scalatest.TestSuite

/** Base trait for specs testing {@link HttpDownloader} */
trait HttpDownloaderSpecBase
  extends BeforeAndAfter { this: TestSuite =>

  val eventMgr = new ControlledEventManager
  val transferMgr = new ControlledTransferManager
  val downloader = new HttpDownloader(eventMgr, transferMgr)

  private val tmpDir = new File(sys.props("java.io.tmpdir"))
  private var tmpFilenames = Seq.empty[String]

  @volatile private var succeeded: Boolean = false
  @volatile var failureOption: Option[Throwable] = None

  val httpPort = 52345
  val httpsPort = 52346
  private var _server: SimpleHttpServer = _
  def server: SimpleHttpServer = Option(_server) getOrElse fail("Server not started!")

  /** Change for debugging to set breakpoints */
  private val waitTimeoutMs = 1500 // * 9999

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
    Option(_server).map(_.shutdown())
    tmpFilenames.foreach {
      new File(tmpDir, _).delete()
    }
  }

  //
  // Helper methods
  //

  protected def startHttpServer(): Unit = {
    _server = new SimpleHttpServer(httpPort, waitTimeoutMs, None, ex => failureOption = Some(ex))
    _server.start()
  }

  protected def startHttpsServer(): Unit = {
    _server = new SimpleHttpServer(httpsPort, waitTimeoutMs, Some(SimpleHttpServer.SelfSignedServerSslContext), ex => failureOption = Some(ex))
    _server.start()
  }

  protected def requestTempFilename(): String = {
    val filename = Random.alphanumeric.take(10).mkString + ".tmp"
    tmpFilenames = filename +: tmpFilenames
    filename
  }

  protected def createDownloadEntry(https: Boolean = false): DownloadEntry = {
    val uri = new URI(s"http${if (https) "s" else ""}://localhost:${if (https) httpsPort else httpPort}/mySubUrl/qwe?a=b&c=d")
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
        failureOption = Some(new IllegalStateException(s"Expected status '${status2}', got '${de.status}'; log: ${de.downloadLog.mkString("\n", "\n", "")}"))
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
      Thread.sleep(50)
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
}
