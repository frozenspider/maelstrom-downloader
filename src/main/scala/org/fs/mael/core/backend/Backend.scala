package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry

/*
 * Backend needs to know
 * - backend-specific download properties
 * UI needs to query backend for
 * - backend-specific UI
 */
trait Backend {
  type BSED <: BackendSpecificEntryData

  def dataClass: Class[BSED]

  val id: String

  def isSupported(uri: URI): Boolean

  /** Create a {@code DownloadEntry} from an URI */
  def create(uri: URI, location: File): DownloadEntry[BSED] = {
    require(isSupported(uri), "URI not supported")
    createInner(uri, location)
  }

  val downloader: BackendDownloader[BSED]

  protected def createInner(uri: URI, location: File): DownloadEntry[BSED]
}
