package org.fs.mael.core.controller

import java.io.File
import java.net.URI
import java.util.UUID

import org.fs.mael.core.controller.view.DownloadUiView

import com.github.nscala_time.time.Imports._

/**
 * Note that this class has no advanced logic and changes won't fire any events.
 *
 * @author FS
 */
// TODO: Do we need this middleware between DownloadEntry and DownloadDetailsView?
final class DownloadDetails(
  var uri:            URI,
  var fileNameOption: Option[String],
  var comment:        String
) extends DownloadUiView {

  override val id: UUID = UUID.randomUUID()

  override val dateCreated: DateTime = DateTime.now()

  var locationOption: Option[File] = None

  /** File name of download if known, display name otherwise */
  var displayName: String = ""

  var sizeOption: Option[Long] = None

  var supportsResumingOption: Option[Boolean] = None

  var speedOption: Option[Long] = None

  var sections: Map[Start, Downloaded] = Map.empty

  var downloadLog: IndexedSeq[LogEntry] = IndexedSeq.empty

  override def addDownloadLogEntry(entry: LogEntry): Unit = {
    this.downloadLog = this.downloadLog :+ entry
  }
}
