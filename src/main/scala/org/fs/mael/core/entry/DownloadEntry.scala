package org.fs.mael.core.entry

import java.io.File
import java.net.URI
import java.util.UUID

import org.fs.mael.core.Status

import com.github.nscala_time.time.Imports._

/**
 * Entry for a specific download processor, implementation details may vary.
 *
 * Note that the entry itself fires no events, they should be fired by the invoker instead.
 *
 * @author FS
 */
abstract class DownloadEntry(
  var uri:            URI,
  var fileNameOption: Option[String],
  var comment:        String
) extends DownloadUiView {

  override val id: UUID = UUID.randomUUID()

  override val dateCreated: DateTime = DateTime.now()

  var locationOption: Option[File] = None

  var displayName: String = uri.toString

  var status: Status = Status.Stopped

  var sizeOption: Option[Long] = None

  var supportsResumingOption: Option[Boolean] = None

  var speedOption: Option[Long] = None

  var sections: Map[Start, Downloaded] = Map.empty

  var downloadLog: IndexedSeq[LogEntry] = IndexedSeq.empty

  override def addDownloadLogEntry(entry: LogEntry): Unit = {
    this.downloadLog = this.downloadLog :+ entry
  }
}
