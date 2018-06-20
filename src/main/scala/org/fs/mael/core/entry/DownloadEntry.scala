package org.fs.mael.core.entry

import java.io.File
import java.net.URI
import java.util.UUID

import scala.collection.mutable

import org.fs.mael.core.Status
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.InMemoryConfigStore

import com.github.nscala_time.time.Imports._

/**
 * Entry for a specific download processor, implementation details may vary.
 *
 * Note that the entry itself fires no events, they should be fired by the invoker instead.
 *
 * @author FS
 */
class DownloadEntry private (
  override val id:          UUID,
  override val dateCreated: DateTime,
  val backendId:            String,
  var uri:                  URI,
  var location:             File,
  var filenameOption:       Option[String],
  var checksumOption:       Option[Checksum],
  var comment:              String,
  val backendSpecificCfg:   InMemoryConfigStore
) extends DownloadEntryView with DownloadEntryLoggableView {

  var status: Status = Status.Stopped

  var sizeOption: Option[Long] = None

  var supportsResumingOption: Option[Boolean] = None

  val sections: mutable.Map[Start, Downloaded] = mutable.Map.empty

  var downloadLog: IndexedSeq[LogEntry] = IndexedSeq.empty

  override def addDownloadLogEntry(entry: LogEntry): Unit = {
    this.downloadLog = this.downloadLog :+ entry
  }
}

object DownloadEntry {
  def apply(
    backendId:          String,
    uri:                URI,
    location:           File,
    filenameOption:     Option[String],
    checksumOption:     Option[Checksum],
    comment:            String,
    backendSpecificCfg: InMemoryConfigStore
  ) = {
    new DownloadEntry(UUID.randomUUID(), DateTime.now(), backendId, uri, location, filenameOption, checksumOption, comment, backendSpecificCfg)
  }

  def load(
    id:                 UUID,
    dateCreated:        DateTime,
    backendId:          String,
    uri:                URI,
    location:           File,
    filenameOption:     Option[String],
    checksumOption:     Option[Checksum],
    comment:            String,
    backendSpecificCfg: InMemoryConfigStore
  ) = {
    new DownloadEntry(id, dateCreated, backendId, uri, location, filenameOption, checksumOption, comment, backendSpecificCfg)
  }
}
