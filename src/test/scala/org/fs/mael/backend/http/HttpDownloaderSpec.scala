package org.fs.mael.backend.http

import java.io.File
import java.io.OutputStream
import java.net.SocketException
import java.net.URI
import java.net.URLEncoder

import scala.io.Codec
import scala.util.Random

import org.apache.http._
import org.apache.http.entity._
import org.fs.mael.backend.http.config.HttpSettings
import org.fs.mael.core.Status
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.checksum.ChecksumType
import org.fs.mael.core.utils.CoreUtils._
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.concurrent.TimeLimits
import org.scalatest.time._
import org.scalatest.exceptions.TestFailedException

@RunWith(classOf[org.scalatestplus.junit.JUnitRunner])
class HttpDownloaderSpec
  extends AnyFunSuite
  with HttpDownloaderSpecBase
  with BeforeAndAfter
  with TimeLimits {

  before {
    super.beforeMethod()
  }

  after {
    super.afterMethod()
  }

  test("regular download of 5 bytes") {
    val de = createDownloadEntry()
    de.checksumOption = Some(Checksum(ChecksumType.MD5, "7cfdd07889b3295d6a550914ab35e068"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)

    startHttpServer()
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("regular download of 5 bytes (HTTPS)") {
    val de = createDownloadEntry(https = true)
    de.backendSpecificCfg.set(HttpSettings.DisableSslValidation, true)
    de.checksumOption = Some(Checksum(ChecksumType.MD5, "7cfdd07889b3295d6a550914ab35e068"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)

    startHttpsServer()
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

    startHttpServer()
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

    startHttpServer()
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

    startHttpServer()
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

    startHttpServer()
    server.respondWith { (req, res) =>
      serveContentNormally(expectedBytes)(req, res)
      res.setHeader("Content-Disposition", """attachment;filename="wrong.zip";filename*=UTF-8''%D0%9E%D0%BD%D0%B8%20%D0%BD%D0%B5%20%D0%BF%D1%80%D0%B8%D0%BB%D0%B5%D1%82%D1%8F%D1%82%20-%20%D1%81%D0%B1%D0%BE%D1%80%D0%BD%D0%B8%D0%BA%20%D1%80%D0%B0%D1%81%D1%81%D0%BA%D0%B0%D0%B7%D0%BE%D0%B2%20%D1%87%D0%B8%D1%82%D0%B0%D0%B5%D1%82%20%D0%90.%20%D0%94%D1%83%D0%BD%D0%B8%D0%BD.zip""")
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

  test("deduce filename from header - YouTube UTF-8 Russian (ISO-8859-1)") {
    val de = createDownloadEntry()
    val filename = "Миллиард.mp4"
    de.filenameOption = None
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)

    startHttpServer()
    server.respondWith { (req, res) =>
      serveContentNormally(expectedBytes)(req, res)
      res.setHeader("Content-Disposition", """attachment; filename="ÐÐ¸Ð»Ð»Ð¸Ð°ÑÐ´.mp4"""")
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

    startHttpServer()
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
    val filename = requestTempFilename("_/?" + _ + "?/_")
    val encodedFilename = URLEncoder.encode(filename, Codec.UTF8.name)
    de.filenameOption = None
    de.uri = new URI(de.uri.toString replaceAllLiterally (de.uri.getPath, s"/$encodedFilename"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)

    startHttpServer()
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(de.filenameOption === Some("___" + filename.drop(3).dropRight(3) + "___"))
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 1)
    assert(transferMgr.bytesRead === 5)
  }

  test("deduce filename from URL - with redirect") {
    val de = createDownloadEntry()
    val oldFilename = requestTempFilename("_/?" + _ + "?/_old")
    val encodedOldFilename = URLEncoder.encode(oldFilename, Codec.UTF8.name)
    de.filenameOption = None
    de.uri = new URI(de.uri.toString replaceAllLiterally (de.uri.getPath, s"/$encodedOldFilename"))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)
    val newFilename = requestTempFilename()

    startHttpServer()
    server.respondWith((req: HttpRequest, res: HttpResponse) => {
      val uri = req.getRequestLine.getUri
      if (uri contains encodedOldFilename) {
        res.setStatusCode(302)
        res.setHeader(HttpHeaders.LOCATION, de.uri.getPath.replaceAllLiterally(oldFilename, newFilename))
      } else if (uri contains newFilename) {
        serveContentNormally(expectedBytes)(req, res)
      } else {
        failureOption = Some(new UnsupportedOperationException("Unexpected request from client! URI = " + uri))
      }
    })

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(de.filenameOption === Some(newFilename))
    assert(readLocalFile(de) === expectedBytes)
    assert(server.reqCounter === 2)
    assert(transferMgr.bytesRead === 5)
  }

  test("deduce filename from entry id (fallback)") {
    val de = createDownloadEntry()
    de.filenameOption = None
    de.uri = new URI(de.uri.toString replaceAllLiterally (de.uri.getPath, ""))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)

    startHttpServer()
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

  test("stopping download right away should interrupt I/O") {
    val de = createDownloadEntry()

    startHttpServer()
    server.respondWith((req, res) => {
      Thread.sleep(30 * 1000)
      failureOption = Some(new UnsupportedOperationException("This should be unreachable!"))
    })

    failAfter(Span(1, Seconds)) {
      expectStatusChangeEvents(de, Status.Running, Status.Stopped)
      downloader.start(de, 999999)
      downloader.stop(de)
      await.firedAndStopped()

      assert(downloader.test_getThreads.isEmpty)
    }
  }

  test("stopping download after connection established should interrupt I/O") {
    val de = createDownloadEntry()
    var connEstablished = false

    startHttpServer()
    server.respondWith((req, res) => {
      connEstablished = true
      Thread.sleep(30 * 1000)
      failureOption = Some(new UnsupportedOperationException("This should be unreachable!"))
    })

    failAfter(Span(1, Seconds)) {
      expectStatusChangeEvents(de, Status.Running, Status.Stopped)
      downloader.start(de, 999999)
      assert(waitUntil(500)(connEstablished))
      downloader.stop(de)
      await.firedAndStopped()

      assert(downloader.test_getThreads.isEmpty)
    }
  }

  test("file size - pre-allocate if known") {
    val de = createDownloadEntry()
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)

    startHttpServer()
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

    startHttpServer()
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

    startHttpServer()
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

  test("failure - SSL cert validation failed") {
    val de = createDownloadEntry(https = true)
    // We're not disabling HTTPS cert validation and our self-signed cert fails it
    de.checksumOption = Some(Checksum(ChecksumType.MD5, "7cfdd07889b3295d6a550914ab35e068"))

    startHttpsServer()
    server.respondWith(serveContentNormally(Array[Byte](1, 2, 3, 4, 5)))

    expectStatusChangeEvents(de, Status.Running, Status.Error)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(getLocalFileOption(de) map (f => !f.exists) getOrElse true)
    assert(server.reqCounter === 0)
    assert(transferMgr.bytesRead === 0)
    assertLastLogEntry(de, "ssl")
  }

  // WARNING: Unstable test!
  // No idea why though
  test("failure - file size changed") {
    val de = createDownloadEntry()
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    startHttpServer()
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

    startHttpServer()
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

    startHttpServer()
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

    startHttpServer()
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

    startHttpServer()
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

  test("failure - server unexpectedly disconnected") {
    val de = createDownloadEntry()
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)

    startHttpServer()
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

    assert(server.reqCounter >= 1) // May be 2+ because of automatic retries
    assert(transferMgr.bytesRead === 2)
    assertLastLogEntry(de, "reset")
  }

  // TODO: Test for file already exits
}
