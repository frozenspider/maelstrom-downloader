package org.fs.mael.core.list

import java.io.File
import java.net.URI

import org.fs.mael.backend.http.HttpBackend
import org.fs.mael.backend.http.HttpEntryData
import org.fs.mael.core.CoreUtils._
import org.fs.mael.core.Status
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.test.StubBackend
import org.fs.mael.test.TestUtils._
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.exceptions.TestFailedException

import com.github.nscala_time.time.Imports._
import org.fs.mael.test.StubEventManager
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import scala.io.Source
import org.fs.mael.test.StubDownloadListSerializer
import org.fs.mael.core.event.Events._

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class DownloadListManagerSpec
  extends FunSuite {

  test("load/save") {
    val file: File = File.createTempFile("dlm-spec-test-file", ".tmp")
    Files.write(file.toPath, "myInitialString".getBytes(StandardCharsets.UTF_8))
    assert(Source.fromFile(file).mkString === "myInitialString")

    val backend = new StubBackend
    val entries = Seq(
      backend.create(new URI("uri1"), new File("/a1"), Some("fn1"), "comment1"),
      backend.create(new URI("uri2"), new File("/a2"), None, "comment2")
    )
    val serializer: DownloadListSerializer = new DownloadListSerializer {
      override def serialize(entries2: Iterable[DownloadEntry[_]]): String = {
        assert(entries2.toSet === entries.toSet)
        "mySerializedString"
      }

      override def deserialize(entriesString: String): Seq[DownloadEntry[_ <: BackendSpecificEntryData]] = {
        assert(entriesString === "myInitialString")
        entries
      }
    }

    val dlm = new DownloadListManager(serializer, file, new StubEventManager)

    // Load
    dlm.load()
    assert(dlm.list() === entries.toSet)

    // Save
    dlm.save()
    assert(Source.fromFile(file).mkString === "mySerializedString")
  }

  test("add/remove") {
    val file: File = new File("")
    val eventMgr = new StubEventManager

    val backend = new StubBackend
    val entries = IndexedSeq(
      backend.create(new URI("uri1"), new File("/a1"), Some("fn1"), "comment1"),
      backend.create(new URI("uri2"), new File("/a2"), None, "comment2")
    )
    val serializer = new StubDownloadListSerializer

    val dlm = new DownloadListManager(serializer, file, eventMgr)

    dlm.add(entries(0))
    assert(dlm.list() === Set(entries(0)))
    assert(eventMgr.events.size === 1)
    assert(eventMgr.events(0) === Added(entries(0)))
    assert(eventMgr.events(0) !== Added(entries(1)))

    dlm.remove(entries(0))
    assert(dlm.list() === Set.empty)
    assert(eventMgr.events.size === 2)
    assert(eventMgr.events(1) === Removed(entries(0)))
    assert(eventMgr.events(1) !== Removed(entries(1)))

    dlm.add(entries(0))
    dlm.add(entries(1))
    assert(dlm.list() === entries.toSet)
    assert(eventMgr.events.size === 4)
    assert(eventMgr.events(2) === Added(entries(0)))
    assert(eventMgr.events(3) === Added(entries(1)))

    dlm.removeAll(entries)
    assert(dlm.list() === Set.empty)
    assert(eventMgr.events.size === 6)
    assert(eventMgr.events(4) === Removed(entries(0)))
    assert(eventMgr.events(5) === Removed(entries(1)))
  }
}
