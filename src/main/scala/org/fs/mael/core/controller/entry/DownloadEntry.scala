package org.fs.mael.core.controller.entry

import java.io.File
import java.net.URI
import java.util.UUID

import org.fs.mael.core.controller.LogEntry

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

  var locationOption: Option[File]

  var displayName: String

  var sizeOption: Option[Long]

  var supportsResumingOption: Option[Boolean]

  var speedOption: Option[Long]

  var sections: Map[Start, Downloaded]

  var downloadLog: IndexedSeq[LogEntry]

  override def addDownloadLogEntry(entry: LogEntry): Unit = {
    this.downloadLog = this.downloadLog :+ entry
  }
}
