package org.fs.mael.core.controller.view

import java.io.File
import java.net.URI
import java.util.UUID

import org.fs.mael.core.controller.LogEntry

import com.github.nscala_time.time.Imports._

/**
 * Protocol-agnostic mutable download details, common for all download types.
 * Data can be updated dynamically by the downloading threads and UI.
 *
 * @author FS
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

  def downloadLog: IndexedSeq[LogEntry]

  override final def equals(obj: Any): Boolean = obj match {
    case dd: DownloadDetailsView => this.id == dd.id
    case _                       => false
  }

  override final val hashCode: Int = id.hashCode()
}
