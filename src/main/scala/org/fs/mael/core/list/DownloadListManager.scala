package org.fs.mael.core.list

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import scala.io.Source

import org.fs.mael.core.Status
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.event.EventManager

class DownloadListManager(
  serializer: DownloadListSerializer,
  file:       File,
  eventMgr:   EventManager
) {
  private var entries: Set[DownloadEntry[_]] = Set.empty

  def load(): Unit = {
    this.synchronized {
      require(entries.isEmpty, "Entries already loaded")
      if (file.exists) {
        val content = Source.fromFile(file).mkString
        if (!content.isEmpty) {
          val entries = serializer.deserialize(content)
          init(entries)
        }
      }
    }
  }

  def save(): Unit = {
    this.synchronized {
      require(!file.exists || file.canWrite, "Can't write to this file")
      val serialized = serializer.serialize(entries)
      file.getParentFile.mkdirs()
      Files.write(file.toPath(), serialized.getBytes(StandardCharsets.UTF_8))
    }
  }

  /** For test usage only! */
  def test_init(entries: Iterable[DownloadEntry[_ <: BackendSpecificEntryData]]): Unit = init(entries)

  /** Called initially upon application start, no event is fired */
  private def init(entries: Iterable[DownloadEntry[_ <: BackendSpecificEntryData]]): Unit = {
    this.synchronized {
      require(this.entries.isEmpty, "Entries already loaded")
      // Mutating code!
      entries.foreach { _.speedOption = None }
      this.entries = entries.collect {
        case de if de.status == Status.Running =>
          de.status = Status.Stopped
          de
        case de =>
          de
      }.toSet
    }
  }

  /** Add a new entry to a download list, firing an event */
  def add(de: DownloadEntry[_]): Unit = {
    this.synchronized {
      entries += de
      eventMgr.fireAdded(de)
    }
  }

  /** Remove an existing entry from a download list, firing an event */
  def remove(de: DownloadEntryView): Unit = {
    this.synchronized {
      de match {
        case de: DownloadEntry[_] =>
          entries -= de
          eventMgr.fireRemoved(de)
      }
    }
  }

  /** Remove existing entries from a download list, firing events */
  def removeAll(des: Iterable[DownloadEntryView]): Unit = {
    this.synchronized {
      des.foreach {
        case de: DownloadEntry[_] => entries -= de
      }
      des foreach eventMgr.fireRemoved
    }
  }

  def list(): Set[DownloadEntry[_]] = {
    this.synchronized {
      entries
    }
  }
}
