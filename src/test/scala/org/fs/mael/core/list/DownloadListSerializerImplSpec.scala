package org.fs.mael.core.list

import java.io.File
import java.net.URI

import org.fs.mael.backend.http.HttpBackend
import org.fs.mael.core.Status
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.checksum.ChecksumType
import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.config.InMemoryConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.transfer.SimpleTransferManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.test.TestUtils._
import org.fs.mael.test.stub.StoringEventManager
import org.fs.mael.test.stub.StubBackend
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.exceptions.TestFailedException

import com.github.nscala_time.time.Imports._

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class DownloadListSerializerImplSpec
  extends FunSuite {

  private val eventMgr = new StoringEventManager

  private val backendMgr = (new BackendManager).withCode { backendMgr =>
    backendMgr += (new StubBackend, Int.MinValue)
    backendMgr += (new HttpBackend(new SimpleTransferManager, new InMemoryConfigStore with IGlobalConfigStore, eventMgr), 0)
  }

  private val serializer = new DownloadListSerializerImpl(backendMgr)

  test("stub - simple") {
    assertSingularSerializationWorks(createDE("simple")())
  }

  test("stub - with all simple fields") {
    assertSingularSerializationWorks(createDE("all-simple-fields") { de =>
      de.location = new File("c:/ewq\\:c").getAbsoluteFile
      de.filenameOption = Some("c:/ewq\\:c")
      de.checksumOption = Some(Checksum(ChecksumType.SHA1, "1abcde"))
      de.comment = "My\nmulti-line\ncomment"
      de.status = Status.Error
      de.sizeOption = Some(12345)
      de.supportsResumingOption = Some(false)
      // speedOption is deliberately ignored
    })
  }

  test("stub - with download log") {
    assertSingularSerializationWorks(createDE("with-download-log") { de =>
      de.addDownloadLogEntry(LogEntry(LogEntry.Info, DateTime.parse("2000-01-01T00:00:00"), "Started"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Request, DateTime.parse("2000-01-02T00:12:34"), "Hey, you!"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Response, DateTime.parse("2000-01-03T01:23:45"), "What?"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Request, DateTime.parse("2000-01-03T01:23:45"), "Take\nthat\nmulti\nline!"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Error, DateTime.now(), "The end"))
    })
  }

  test("stub - with sections") {
    assertSingularSerializationWorks(createDE("with-sections") { de =>
      de.sections += (0L -> 10L)
      de.sections += (20L -> 30L)
    })
  }

  test("stub - with backend-specific field") {
    assertSingularSerializationWorks(createDE("with-backend-specific-field") {
      case de: DownloadEntry =>
        assert(de.backendId === StubBackend.Id)
        de.backendSpecificCfg.set(StubBackend.StubSetting, "Hey there! Д™©®±ђ±\u0000‡†/\\")
    })
  }

  test("http - both simple and specific fields") {
    assertSingularSerializationWorks(createDE("http://www.example.com/subresource?a=b&c=d") {
      case de: DownloadEntry =>
        assert(de.backendId === HttpBackend.Id)
        de.sizeOption = Some(12345678)
        de.sections += (0L -> 10L)
        de.sections += (200L -> 300L)
    })
  }

  test("stub + http") {
    val de1 = createDE("all-simple-fields") {
      case de: DownloadEntry =>
        assert(de.backendId === StubBackend.Id)
        de.comment = "comment 1"
        de.backendSpecificCfg.set(StubBackend.StubSetting, "Hey there!")
    }
    val de2 = createDE("http://www.example.com/subresource?a=b&c=d") {
      case de: DownloadEntry =>
        assert(de.backendId === HttpBackend.Id)
        de.sizeOption = Some(12345678)
        de.comment = "comment 2"
        de.sections += (0L -> 10L)
        de.sections += (200L -> 300L)
    }
    val serialized = serializer.serialize(Seq(de1, de2))
    val deserialized = serializer.deserialize(serialized)
    assert(deserialized.size === 2)
    assertDownloadEntriesEqual(de1, deserialized(0))
    assertDownloadEntriesEqual(de2, deserialized(1))
    intercept[TestFailedException] {
      assertDownloadEntriesEqual(de1, deserialized(1))
    }
    intercept[TestFailedException] {
      assertDownloadEntriesEqual(de2, deserialized(0))
    }
  }

  private def assertSingularSerializationWorks(de1: DownloadEntry): Unit = {
    val serialized = serializer.serialize(Seq(de1))
    val deserialized = serializer.deserialize(serialized)
    assert(deserialized.size === 1)
    assertDownloadEntriesEqual(de1, deserialized.head)
  }

  private def createDE(uriString: String)(code: (DownloadEntry => Unit) = (de => ())): DownloadEntry = {
    val loc = new File(System.getProperty("java.io.tmpdir"))
    val uri = new URI(uriString)
    val backend = backendMgr.findFor(uri).get
    backend.create(uri, loc, None, None, "", None).withCode(code)
  }
}
