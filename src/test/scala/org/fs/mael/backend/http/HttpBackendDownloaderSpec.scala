package org.fs.mael.backend.http

import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files

import scala.util.Random

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
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite
import org.slf4s.Logging

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HttpBackendDownloaderSpec
  extends FunSuite
  with BeforeAndAfter
  with BeforeAndAfterAll {

  private type DE = DownloadEntry[HttpEntryData]

  private val eventMgr = new ControlledEventManager
  private val transferMgr = new ControlledTransferManager

  private val downloader = new HttpBackendDownloader(eventMgr, transferMgr)
  private val tmpDir = new File(sys.props("java.io.tmpdir"))
  private var tmpFilenames = Seq.empty[String]

  @volatile private var succeeded: Boolean = false
  @volatile private var failureOption: Option[Exception] = None

  private val port = 52345
  private val server: SimpleHttpServer = new SimpleHttpServer(port)

  /** Change for debugging to set breakpoints */
  private val waitTimeoutMs = 1000 * 9999

  before {
    eventMgr.reset()
    transferMgr.reset()
    failureOption = None
    succeeded = false
    tmpFilenames = Seq.empty
  }

  after {
    tmpFilenames.foreach {
      new File(tmpDir, _).delete()
    }
  }

  override def afterAll() {
    server.shutdown()
  }

  test("regular download of 5 bytes") {
    val de = createDownloadEntry
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.start()
    downloader.start(de, 999999)
    waitFor.firedAndStopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("download 5 bytes, stop, download 5 more") {
    val de = createDownloadEntry
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Stopped)
    transferMgr.throttleBytes(5)
    transferMgr.start()
    downloader.start(de, 999999)
    waitFor.read(5)
    downloader.stop(de)
    waitFor.stopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
    assert(readLocalFile(de) === expectedBytes.take(5) ++ Seq.fill[Byte](5)(0x00))
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)

    eventMgr.reset()
    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.reset()
    transferMgr.start()
    downloader.start(de, 999999)
    waitFor.firedAndStopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 2)
    assert(transferMgr.bytesRead === 5)
  }

  test("deduce filename from header") {
    val de = createDownloadEntry
    val filename = de.filenameOption.get
    de.filenameOption = None
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith { (req, res) =>
      serveContentNormally(expectedBytes)(req, res)
      res.setHeader("Content-Disposition", s"Attachment; filename=/?${filename}?/")
    }

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.start()
    downloader.start(de, 999999)
    waitFor.firedAndStopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
    assert(de.filenameOption === Some("__" + filename + "__"))
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("deduce filename from URL") {
    val de = createDownloadEntry
    val filename = de.filenameOption.get
    val encodedFilename = URLEncoder.encode(s"/?${filename}?/", "UTF8")
    de.filenameOption = None
    de.uri = new URI(de.uri.toString replaceAllLiterally (de.uri.getPath, s"/$encodedFilename"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.start()
    downloader.start(de, 999999)
    waitFor.firedAndStopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
    assert(de.filenameOption === Some("__" + filename + "__"))
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("deduce filename from entry id (fallback)") {
    val de = createDownloadEntry
    de.filenameOption = None
    de.uri = new URI(de.uri.toString replaceAllLiterally (de.uri.getPath, ""))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.start()
    downloader.start(de, 999999)
    waitFor.firedAndStopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
    assert(de.filenameOption.isDefined)
    assert(de.filenameOption.get === "file-" + de.id.toString.toUpperCase)
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("file size - pre-allocate if known") {
    val de = createDownloadEntry
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.start()
    transferMgr.throttleBytes(0)
    downloader.start(de, 999999)
    waitFor.fileAppears(de)
    assert(readLocalFile(de).size === 5)
    assert(readLocalFile(de) === Seq.fill[Byte](5)(0x00))
    transferMgr.throttleBytes(999)
    waitFor.firedAndStopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
  }

  test("file size - expand dynamically if unknown") {
    val de = createDownloadEntry
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    server.respondWith { (req, res) =>
      res.setStatusCode(HttpStatus.SC_OK)
      val entity = new ByteArrayEntity(expectedBytes, ContentType.APPLICATION_OCTET_STREAM) {
        override def getContentLength = -1
      }
      res.setEntity(entity)
    }

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.start()
    transferMgr.throttleBytes(0)
    downloader.start(de, 999999)
    waitFor.fileAppears(de)
    assert(readLocalFile(de).size === 0)

    transferMgr.throttleBytes(5)
    waitFor.read(5)
    assert(readLocalFile(de).size === 5)

    transferMgr.throttleBytes(999)
    waitFor.read(10)
    assert(readLocalFile(de).size === 10)
    waitFor.firedAndStopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
    assert(server.reqCounter === 1)
  }

  test("failure - server responds with an error") {
    val de = createDownloadEntry
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith { (req, res) =>
      res.setStatusCode(HttpStatus.SC_FORBIDDEN)
    }
    expectStatusChangeEvents(de, Status.Running, Status.Error)
    transferMgr.start()
    downloader.start(de, 999999)
    waitFor.firedAndStopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
    assert(getLocalFileOption(de) map (f => !f.exists) getOrElse true)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 0)
  }

  test("failure - file size changed") {
    val de = createDownloadEntry
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    server.respondWith { (req, res) =>
      res.setStatusCode(if (req.getHeaders(HttpHeaders.RANGE).isEmpty) HttpStatus.SC_OK else HttpStatus.SC_PARTIAL_CONTENT)
      val entity = new ByteArrayEntity(expectedBytes, ContentType.APPLICATION_OCTET_STREAM) {
        override def getContentLength = Random.nextInt(1000) + 1
      }
      res.setEntity(entity)
    }

    expectStatusChangeEvents(de, Status.Running, Status.Stopped)
    transferMgr.start()
    transferMgr.throttleBytes(5)
    downloader.start(de, 999999)
    waitFor.read(5)

    downloader.stop(de)
    waitFor.stopped()

    eventMgr.reset()
    expectStatusChangeEvents(de, Status.Running, Status.Error)
    transferMgr.reset()
    transferMgr.start()
    downloader.start(de, 999999)
    waitFor.firedAndStopped()

    failureOption foreach (ex => fail(ex))
    assert(succeeded)
    assert(server.reqCounter === 2)
  }

  //
  // Helper methods
  //

  private def requestTempFilename(): String = {
    val filename = Random.alphanumeric.take(10).mkString + ".tmp"
    tmpFilenames = filename +: tmpFilenames
    filename
  }

  private def createDownloadEntry: DE = {
    val uri = new URI(s"http://localhost:$port/mySubUrl/qwe?a=b&c=d")
    val filename = requestTempFilename()
    DownloadEntry[HttpEntryData](
      backendId           = HttpBackend.Id,
      uri                 = uri,
      location            = tmpDir,
      filenameOption      = Some(filename),
      comment             = "my comment",
      backendSpecificData = new HttpEntryData
    )
  }

  /** Expect downloader to fire status changed to status1, and then - to status2 */
  private def expectStatusChangeEvents(de: DE, status1: Status, status2: Status): Unit = {
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
        failureOption = Some(new IllegalStateException(s"Unexpected status ${de.status}"))
    }
  }

  private def getLocalFileOption(de: DE): Option[File] = {
    de.filenameOption map (new File(tmpDir, _))
  }

  private def readLocalFile(de: DE): Array[Byte] = {
    Files.readAllBytes(getLocalFileOption(de).get.toPath)
  }

  /** Used for server to act like a regular HTTP server serving a file, supporting resuming */
  private def serveContentNormally(content: Array[Byte]) =
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

  private object waitFor {
    /** Wait for all expected events to fire (or unexpected to cause failure) and for all download threads to die */
    def firedAndStopped(): Unit = {
      val waitUntilProcessed = waitUntil(waitTimeoutMs) {
        (succeeded || failureOption.isDefined) && downloader.test_getThreads.isEmpty
      }
      assert(waitUntilProcessed)
    }

    /** Wait for X bytes to be read from transfer manager (since last reset) */
    def read(x: Int): Unit = {
      val waitUntilRead = waitUntil(waitTimeoutMs) {
        transferMgr.bytesRead == x || failureOption.isDefined
      }
      assert(waitUntilRead)
    }

    /** Wait for all download threads to die */
    def stopped(): Unit = {
      val waitUntilStopped = waitUntil(waitTimeoutMs) {
        downloader.test_getThreads.isEmpty
      }
      assert(waitUntilStopped)
    }

    /** Wait for the file associated with download entry to appear on disc */
    def fileAppears(de: DE): Unit = {
      val waitUntilFileAppears = waitUntil(waitTimeoutMs) {
        getLocalFileOption(de) map (_.exists) getOrElse false
      }
      assert(waitUntilFileAppears)
    }
  }

  //
  // Helper classes
  //

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

  private class SimpleHttpServer(port: Int) extends Logging { self =>
    import java.net.SocketException
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
