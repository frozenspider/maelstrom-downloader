package org.fs.mael.core

import java.net.URI

import org.fs.mael.core.entry.DownloadEntry
import java.io.File

/*
 * Backend needs to know
 * - backend-specific download properties
 * UI needs to query backend for
 * - backend-specific UI
 */
trait Backend {
  type DE <: DownloadEntry

  def entryClass: Class[DE]

  val id: String

  def isSupported(uri: URI): Boolean

  /** Create a {@code DownloadEntry} from an URI */
  def create(uri: URI, location: File): DE = {
    require(isSupported(uri), "URI not supported")
    createInner(uri, location)
  }

  val downloader: BackendDownloader[DE]

  protected def createInner(uri: URI, location: File): DE

  //  def start(de: DE): Unit

  //  def stop(de: DE): Unit
}
