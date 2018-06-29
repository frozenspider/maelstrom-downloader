package org.fs.mael.core.list

import java.io.File
import java.nio.file.Files

import scala.io.Codec
import scala.io.Source

import org.fs.mael.core.Status
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.event.EventManager

class DownloadListManager(
  serializer: DownloadListSerializer,
  file:       File,
  eventMgr:   EventManager
) {
  private var entries: IndexedSeq[DownloadEntry] = IndexedSeq.empty

  def load(): Unit = {
    this.synchronized {
      require(entries.isEmpty, "Entries already loaded")
      if (file.exists) {
        val content = Source.fromFile(file)(Codec.UTF8).mkString
        if (!content.isEmpty) {
          val entries = serializer.deserialize(content)
          init(entries)
        }
      }
    }
  }

  // TODO: Autosave
  // TODO: Only save last 100 (?) entries from download log
  def save(): Unit = {
    this.synchronized {
      require(!file.exists || file.canWrite, "Can't write to this file")
      val serialized = serializer.serialize(entries)
      file.getParentFile.mkdirs()
      Files.write(file.toPath(), serialized.getBytes(Codec.UTF8.charSet))
    }
  }

  /** For test usage only! */
  def test_init(entries: Iterable[DownloadEntry]): Unit = init(entries)

  /** Called initially upon application start, no event is fired */
  private def init(entries: Iterable[DownloadEntry]): Unit = {
    this.synchronized {
      require(this.entries.isEmpty, "Entries already loaded")
      // Mutating code!
      this.entries = entries.collect {
        case de if de.status == Status.Running =>
          de.status = Status.Stopped
          de
        case de =>
          de
      }.toIndexedSeq
    }
  }

  /** Add a new entry to a download list if it's not already there, firing an event */
  def add(de: DownloadEntry): Boolean = {
    this.synchronized {
      if (!entries.contains(de)) {
        entries = entries :+ de
        eventMgr.fireAdded(de)
        true
      } else {
        false
      }
    }
  }

  /** Remove an existing entry from a download list, firing an event */
  def remove(de: DownloadEntry): Unit = {
    this.synchronized {
      entries = entries.filter(_ != de)
      eventMgr.fireRemoved(de)
    }
  }

  /** Remove existing entries from a download list, firing events */
  def removeAll(des: Iterable[DownloadEntry]): Unit = {
    this.synchronized {
      des foreach (de => entries = entries.filter(_ != de))
      des foreach eventMgr.fireRemoved
    }
  }

  def list(): IndexedSeq[DownloadEntry] = {
    this.synchronized {
      entries
    }
  }
}
