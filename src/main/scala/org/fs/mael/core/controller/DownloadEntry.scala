package org.fs.mael.core.controller

import java.io.File
import java.net.URI
import java.util.UUID

import com.github.nscala_time.time.Imports._

class DownloadEntry(
  var uri:            URI,
  var fileNameOption: Option[String],
  var comment:        String
) {
  type Start = Long
  type Downloaded = Long

  val id: UUID = UUID.randomUUID()

  val dateCreated: DateTime = DateTime.now()

  var locationOption: Option[File] = None

  /** File name of download if known, display name otherwise */
  var displayName: String = ""

  var sizeOption: Option[Long] = None

  def downloadedSize: Long = {
    sections.values.sum
  }

  /** Whether resuming is supported, if known */
  var supportsResumingOption: Option[Boolean] = None

  var speedOption: Option[Long] = None

  var sections: Map[Start, Downloaded] = Map.empty
}
