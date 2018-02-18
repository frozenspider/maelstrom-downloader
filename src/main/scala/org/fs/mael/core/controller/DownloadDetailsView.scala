package org.fs.mael.core.controller

import java.io.File
import java.net.URI
import java.util.UUID

import com.github.nscala_time.time.Imports._

/**
 * Immutable view of {@code DownloadDetails}
 */
trait DownloadDetailsView {
  type Start = Long
  type Downloaded = Long

  def id: UUID

  def dateCreated: DateTime

  def uri: URI
  def fileNameOption: Option[String]
  def comment: String

  def locationOption: Option[File]

  /** File name of download if known, display name otherwise */
  def displayName: String

  def sizeOption: Option[Long]

  def downloadedSize: Long = {
    sections.values.sum
  }

  /** Whether resuming is supported, if known */
  def supportsResumingOption: Option[Boolean]

  def speedOption: Option[Long]

  def sections: Map[Start, Downloaded]
}
