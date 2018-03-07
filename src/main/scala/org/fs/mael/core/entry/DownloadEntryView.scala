package org.fs.mael.core.entry

import java.io.File
import java.net.URI
import java.util.UUID

import scala.collection.MapLike

import org.fs.mael.core.Status
import org.fs.mael.core.checksum.Checksum

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

  def backendId: String

  def id: UUID

  def dateCreated: DateTime

  def uri: URI

  def location: File

  def filenameOption: Option[String]

  def checksumOption: Option[Checksum]
  
  def comment: String

  /** File name of download if known, display name otherwise */
  def displayName: String = filenameOption getOrElse uri.toString

  def status: Status

  def sizeOption: Option[Long]

  def downloadedSize: Long = {
    sections.values.sum
  }

  /** Whether resuming is supported, if known */
  def supportsResumingOption: Option[Boolean]

  def speedOption: Option[Long]

  def sections: MapLike[Start, Downloaded, _]

  def downloadLog: IndexedSeq[LogEntry]

  override final def equals(obj: Any): Boolean = obj match {
    case dd: DownloadEntryView => this.id == dd.id
    case _                     => false
  }

  override final lazy val hashCode: Int = id.hashCode()
}
