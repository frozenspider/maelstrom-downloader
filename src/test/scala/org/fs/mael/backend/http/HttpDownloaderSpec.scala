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
import org.fs.mael.core.Status
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.checksum.ChecksumType
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HttpDownloaderSpec
  extends FunSuite
  with HttpDownloaderSpecBase
  with BeforeAndAfter
  with BeforeAndAfterAll {

  override protected def afterAll() = {
    super.afterAll()
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

  // WARNING: Unstable test!
  // No idea why though
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
}
