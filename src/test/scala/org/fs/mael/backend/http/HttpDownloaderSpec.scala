package org.fs.mael.backend.http

import java.io.File
import java.io.OutputStream
import java.net.SocketException
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files

import scala.io.Codec
import scala.util.Random

import org.apache.http._
import org.apache.http.entity._
import org.fs.mael.core.Status
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.checksum.ChecksumType
import org.fs.mael.core.config.InMemoryConfigStore
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
class HttpDownloaderSpec
  extends FunSuite
  with BeforeAndAfter
  with BeforeAndAfterAll {

  private val eventMgr = new ControlledEventManager
  private val transferMgr = new ControlledTransferManager

  private val downloader = new HttpDownloader(eventMgr, transferMgr)
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
    val de = createDownloadEntry()
    de.checksumOption = Some(Checksum(ChecksumType.MD5, "7cfdd07889b3295d6a550914ab35e068"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("download 5 bytes, stop, download 5 more") {
    val de = createDownloadEntry()
    de.checksumOption = Some(Checksum(ChecksumType.MD5, "70903e79b7575e3f4e7ffa15c2608ac7"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    server.respondWith { (req, res) =>
      serveContentNormally(expectedBytes)(req, res)
      res.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes")
    }

    expectStatusChangeEvents(de, Status.Running, Status.Stopped)
    transferMgr.throttleBytes(5)
    downloader.start(de, 999999)
    await.read(5)

    downloader.stop(de)
    await.stopped()

    assertDoesntHaveLogEntry(de, "doesn't support resuming")
    assert(readLocalFile(de) === expectedBytes.take(5) ++ Seq.fill[Byte](5)(0x00))
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)

    eventMgr.reset()
    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.reset()
    downloader.start(de, 999999)
    await.firedAndStopped()

    assertDoesntHaveLogEntry(de, "starting over")
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 2)
    assert(transferMgr.bytesRead === 5)
  }

  test("download 5 bytes, stop, redownload file with unsupported resuming") {
    val de = createDownloadEntry()
    de.checksumOption = Some(Checksum(ChecksumType.MD5, "70903e79b7575e3f4e7ffa15c2608ac7"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Stopped)
    transferMgr.throttleBytes(5)
    downloader.start(de, 999999)
    await.read(5)

    downloader.stop(de)
    await.stopped()

    assertHasLogEntry(de, "doesn't support resuming")
    assert(readLocalFile(de) === expectedBytes.take(5) ++ Seq.fill[Byte](5)(0x00))
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)

    eventMgr.reset()
    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.reset()
    downloader.start(de, 999999)
    await.firedAndStopped()

    assertHasLogEntry(de, "starting over")
    assert(server.reqCounter === 2)
    assert(transferMgr.bytesRead === 10)
  }

  test("deduce filename from header - simple") {
    val de = createDownloadEntry()
    val filename = de.filenameOption.get
    de.filenameOption = None
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith { (req, res) =>
      serveContentNormally(expectedBytes)(req, res)
      res.setHeader("Content-Disposition", s"Attachment; filename=/?${filename}?/")
    }

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(de.filenameOption === Some("__" + filename + "__"))
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("deduce filename from header - Google Drive UTF-8 Russian") {
    val de = createDownloadEntry()
    val filename = "Они не прилетят - сборник рассказов читает А. Дунин.zip"
    de.filenameOption = None
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith { (req, res) =>
      serveContentNormally(expectedBytes)(req, res)
      res.setHeader("Content-Disposition", """attachment;filename="___ __ ________ - _______ _________ ______ _. _____.zip";filename*=UTF-8''%D0%9E%D0%BD%D0%B8%20%D0%BD%D0%B5%20%D0%BF%D1%80%D0%B8%D0%BB%D0%B5%D1%82%D1%8F%D1%82%20-%20%D1%81%D0%B1%D0%BE%D1%80%D0%BD%D0%B8%D0%BA%20%D1%80%D0%B0%D1%81%D1%81%D0%BA%D0%B0%D0%B7%D0%BE%D0%B2%20%D1%87%D0%B8%D1%82%D0%B0%D0%B5%D1%82%20%D0%90.%20%D0%94%D1%83%D0%BD%D0%B8%D0%BD.zip""")
    }

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    try {
      downloader.start(de, 999999)
      await.firedAndStopped()

      assert(de.filenameOption === Some(filename))
      assert(readLocalFile(de) === expectedBytes)
      assert(server.reqCounter === 1)
      assert(transferMgr.bytesRead === 5)
    } finally {
      de.fileOption foreach { _.delete() }
    }
  }

  test("deduce filename from header - illegal filename*") {
    val de = createDownloadEntry()
    de.filenameOption = None
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith { (req, res) =>
      serveContentNormally(expectedBytes)(req, res)
      res.setHeader("Content-Disposition", """attachment;filename*=somerandomgibberish""")
    }

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    try {
      downloader.start(de, 999999)
      await.firedAndStopped()

      assert(de.filenameOption === Some("qwe"))
      assert(readLocalFile(de) === expectedBytes)
      assert(server.reqCounter === 1)
      assert(transferMgr.bytesRead === 5)

      assertHasLogEntry(de, "malformed filename*")
    } finally {
      de.fileOption foreach { _.delete() }
    }
  }

  test("deduce filename from URL") {
    val de = createDownloadEntry()
    val filename = de.filenameOption.get
    val encodedFilename = URLEncoder.encode(s"/?${filename}?/", Codec.UTF8.name)
    de.filenameOption = None
    de.uri = new URI(de.uri.toString replaceAllLiterally (de.uri.getPath, s"/$encodedFilename"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(de.filenameOption === Some("__" + filename + "__"))
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("deduce filename from entry id (fallback)") {
    val de = createDownloadEntry()
    de.filenameOption = None
    de.uri = new URI(de.uri.toString replaceAllLiterally (de.uri.getPath, ""))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(de.filenameOption.isDefined)
    assert(de.filenameOption.get === "file-" + de.id.toString.toUpperCase)
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("file size - pre-allocate if known") {
    val de = createDownloadEntry()
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.throttleBytes(0)
    downloader.start(de, 999999)
    await.fileAppears(de)
    assert(readLocalFile(de).size === 5)
    assert(readLocalFile(de) === Seq.fill[Byte](5)(0x00))

    transferMgr.throttleBytes(999)
    await.firedAndStopped()
    assert(readLocalFile(de).size === 5)
    assert(readLocalFile(de) === expectedBytes)
  }

  test("file size - expand dynamically if unknown") {
    val de = createDownloadEntry()
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    server.respondWith { (req, res) =>
      res.setStatusCode(HttpStatus.SC_OK)
      res.setEntity(new ByteArrayEntity(expectedBytes, ContentType.APPLICATION_OCTET_STREAM) {
        override def getContentLength = -1
      })
    }

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    transferMgr.throttleBytes(0)
    downloader.start(de, 999999)
    await.fileAppears(de)
    assert(readLocalFile(de).size === 0)

    transferMgr.throttleBytes(5)
    await.read(5)
    assert(readLocalFile(de).size === 5)

    transferMgr.throttleBytes(999)
    await.read(10)
    assert(readLocalFile(de).size === 10)
    await.firedAndStopped()

    assert(server.reqCounter === 1)
  }

  test("failure - server responds with an error") {
    val de = createDownloadEntry()
    server.respondWith { (req, res) =>
      res.setStatusCode(HttpStatus.SC_FORBIDDEN)
    }
    expectStatusChangeEvents(de, Status.Running, Status.Error)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(getLocalFileOption(de) map (f => !f.exists) getOrElse true)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 0)
    assertLastLogEntry(de, "responded")
  }

  test("failure - file size changed") {
    val de = createDownloadEntry()
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    server.respondWith { (req, res) =>
      res.setStatusCode(if (req.getHeaders(HttpHeaders.RANGE).isEmpty) HttpStatus.SC_OK else HttpStatus.SC_PARTIAL_CONTENT)
      res.setEntity(new ByteArrayEntity(expectedBytes, ContentType.APPLICATION_OCTET_STREAM) {
        override def getContentLength = Random.nextInt(1000) + 1
      })
    }

    expectStatusChangeEvents(de, Status.Running, Status.Stopped)
    transferMgr.throttleBytes(5)
    downloader.start(de, 999999)
    await.read(5)

    downloader.stop(de)
    await.stopped()

    eventMgr.reset()
    expectStatusChangeEvents(de, Status.Running, Status.Error)
    transferMgr.reset()
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(server.reqCounter === 2)
    assertLastLogEntry(de, "size")
  }

  test("failure - local file disappeared") {
    val de = createDownloadEntry()
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Stopped)
    transferMgr.throttleBytes(5)
    downloader.start(de, 999999)
    await.read(5)

    downloader.stop(de)
    await.stopped()

    assert(server.reqCounter === 1)
    assert(getLocalFileOption(de).isDefined && getLocalFileOption(de).get.exists)
    getLocalFileOption(de).get.delete()

    eventMgr.reset()
    expectStatusChangeEvents(de, Status.Running, Status.Error)
    transferMgr.reset()
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(server.reqCounter === 1) // No request is issued
    assertLastLogEntry(de, "file")
  }

  test("failure - path is not accessible") {
    val de = createDownloadEntry()
    de.location = new File("?*|\u0000><'\"%:")
    server.respondWith(serveContentNormally(Array.empty[Byte]))

    expectStatusChangeEvents(de, Status.Running, Status.Error)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(server.reqCounter === 0)
    assertLastLogEntry(de, "path")
  }

  test("failure - checksum doesn't match") {
    val de = createDownloadEntry()
    de.checksumOption = Some(Checksum(ChecksumType.MD5, "7cfdd07889b3295d6a550914ab35e067"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Error)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
    assertLastLogEntry(de, "match")
  }

  // WARNING: Unstable test!
  // server.reqCounter is sometimes 0
  test("failure - request timeout") {
    val de = createDownloadEntry()
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith { (req, res) =>
      Thread.sleep(200)
      serveContentNormally(expectedBytes)(req, res)
    }

    expectStatusChangeEvents(de, Status.Running, Status.Error)
    downloader.start(de, 100)
    await.firedAndStopped()

    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 0)
    assertLastLogEntry(de, "timed out")
  }

  // WARNING: Unstable test!
  // server.reqCounter is sometimes 2
  test("failure - server unexpectedly disconnected") {
    val de = createDownloadEntry()
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    server.respondWith { (req, res) =>
      res.setStatusCode(HttpStatus.SC_OK)
      res.setEntity(new ByteArrayEntity(expectedBytes, ContentType.APPLICATION_OCTET_STREAM) {
        override def writeTo(os: OutputStream): Unit = {
          os.write(expectedBytes, 0, 2)
          os.flush()
          throw new SocketException("D'oh!")
        }
      })
    }

    expectStatusChangeEvents(de, Status.Running, Status.Error)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 2)
    assertLastLogEntry(de, "reset")
  }

  // TODO: Test for file already exits

  //
  // Helper methods
  //

  private def requestTempFilename(): String = {
    val filename = Random.alphanumeric.take(10).mkString + ".tmp"
    tmpFilenames = filename +: tmpFilenames
    filename
  }

  private def createDownloadEntry(): DownloadEntry = {
    val uri = new URI(s"http://localhost:$port/mySubUrl/qwe?a=b&c=d")
    val filename = requestTempFilename()
    DownloadEntry(
      backendId          = HttpBackend.Id,
      uri                = uri,
      location           = tmpDir,
      filenameOption     = Some(filename),
      checksumOption     = None,
      comment            = "my comment",
      backendSpecificCfg = new InMemoryConfigStore
    )
  }

  /** Expect downloader to fire status changed to status1, and then - to status2 */
  private def expectStatusChangeEvents(de: DownloadEntry, status1: Status, status2: Status): Unit = {
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

  private def getLocalFileOption(de: DownloadEntry): Option[File] = {
    de.filenameOption map (new File(tmpDir, _))
  }

  private def readLocalFile(de: DownloadEntry): Array[Byte] = {
    Files.readAllBytes(getLocalFileOption(de).get.toPath)
  }

  private def assertHasLogEntry(de: DownloadEntry, substr: String): Unit = {
    val hasLogEntry = de.downloadLog.exists(_.details.toLowerCase contains substr)
    assert(hasLogEntry, s": '$substr'")
  }

  private def assertDoesntHaveLogEntry(de: DownloadEntry, substr: String): Unit = {
    val hasLogEntry = de.downloadLog.exists(_.details.toLowerCase contains substr)
    assert(!hasLogEntry, s": '$substr'")
  }

  private def assertLastLogEntry(de: DownloadEntry, substr: String): Unit = {
    val lastLogEntry = de.downloadLog.last.details.toLowerCase contains substr
    assert(lastLogEntry, s": '$substr'; was ${de.downloadLog.last}")
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

  private object await {
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
