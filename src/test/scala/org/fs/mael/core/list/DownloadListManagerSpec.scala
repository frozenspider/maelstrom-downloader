package org.fs.mael.core.list

import java.io.File
import java.net.URI
import java.nio.file.Files

import scala.io.Codec
import scala.io.Source

import org.fs.mael.core.Status
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.checksum.ChecksumType
import org.fs.mael.core.config.InMemoryConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.Events._
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.test.stub.StoringEventManager
import org.fs.mael.test.stub.StubBackend
import org.fs.mael.test.stub.StubDownloadListSerializer
import org.junit.runner.RunWith
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class DownloadListManagerSpec
  extends FunSuite {

  test("load/save") {
    val file: File = File.createTempFile("dlm-spec-test-file", ".tmp")
    Files.write(file.toPath, "myInitialString".getBytes(Codec.UTF8.charSet))
    assert(Source.fromFile(file).mkString === "myInitialString")

    val backend = new StubBackend
    val entries = Seq(
      backend.create(new URI("uri1"), new File("/a1"), Some("fn1"), None, "comment1", Some(defaultCfg)),
      backend.create(new URI("uri2"), new File("/a2"), None, Some(Checksum(ChecksumType.SHA1, "1abcde")), "comment2", None)
    )
    val serializer: DownloadListSerializer = new DownloadListSerializer {
      override def serialize(entries2: Iterable[DownloadEntry]): String = {
        assert(entries2 === entries)
        "mySerializedString"
      }

      override def deserialize(entriesString: String): Seq[DownloadEntry] = {
        assert(entriesString === "myInitialString")
        entries
      }
    }

    val dlm = new DownloadListManager(serializer, file, new StoringEventManager)

    // Load
    dlm.load()
    assert(dlm.list() === entries)

    // Save
    dlm.save()
    assert(Source.fromFile(file).mkString === "mySerializedString")
  }

  test("init should replace Running status with Stopped") {
    val eventMgr = new StoringEventManager
    val dlm = new DownloadListManager(new StubDownloadListSerializer, new File(""), eventMgr)

    val backend = new StubBackend
    val statuses = Seq(
      Status.Running,
      Status.Stopped,
      Status.Error,
      Status.Complete
    )
    val entries = statuses.zipWithIndex.map {
      case (status, i) =>
        backend.create(new URI("uri" + i), new File(""), None, None, "", None).withCode {
          _.status = status
        }
    }

    dlm.test_init(entries)
    assert(dlm.list().toSeq.sortBy(_.uri.toString).map(_.status) === Seq(
      Status.Stopped,
      Status.Stopped,
      Status.Error,
      Status.Complete
    ))
  }

  test("add/remove") {
    val eventMgr = new StoringEventManager
    val dlm = new DownloadListManager(new StubDownloadListSerializer, new File(""), eventMgr)

    val backend = new StubBackend
    val entries = IndexedSeq(
      backend.create(new URI("uri1"), new File("/a1"), Some("fn1"), None, "comment1", Some(defaultCfg)),
      backend.create(new URI("uri2"), new File("/a2"), None, Some(Checksum(ChecksumType.SHA1, "1abcde")), "comment2", None)
    )

    dlm.add(entries(0))
    assert(dlm.list() === Seq(entries(0)))
    assert(eventMgr.events.size === 1)
    assert(eventMgr.events(0) === Added(entries(0)))
    assert(eventMgr.events(0) !== Added(entries(1)))

    dlm.remove(entries(0))
    assert(dlm.list() === Seq.empty)
    assert(eventMgr.events.size === 2)
    assert(eventMgr.events(1) === Removed(entries(0)))
    assert(eventMgr.events(1) !== Removed(entries(1)))

    dlm.add(entries(0))
    dlm.add(entries(1))
    assert(dlm.list() === entries)
    assert(eventMgr.events.size === 4)
    assert(eventMgr.events(2) === Added(entries(0)))
    assert(eventMgr.events(3) === Added(entries(1)))

    dlm.removeAll(entries)
    assert(dlm.list() === Seq.empty)
    assert(eventMgr.events.size === 6)
    assert(eventMgr.events(4) === Removed(entries(0)))
    assert(eventMgr.events(5) === Removed(entries(1)))
  }

  private def defaultCfg: InMemoryConfigStore = {
    val cfg = new InMemoryConfigStore
    cfg.initDefault(StubBackend.StubSetting)
    cfg
  }
}
