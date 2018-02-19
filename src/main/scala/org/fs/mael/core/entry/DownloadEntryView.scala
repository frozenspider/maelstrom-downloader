package org.fs.mael.core.entry

import java.io.File
import java.net.URI
import java.util.UUID

import com.github.nscala_time.time.Imports._

/**
 * Backend-agnostic mutable download details, common for all download types.
 * Data can be updated dynamically by the downloading threads and UI.
 *
 * @author FS
 */
trait DownloadEntryView {
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

  def downloadLog: IndexedSeq[LogEntry]

  override final def equals(obj: Any): Boolean = obj match {
    case dd: DownloadEntryView => this.id == dd.id
    case _                       => false
  }

  override final val hashCode: Int = id.hashCode()
}
