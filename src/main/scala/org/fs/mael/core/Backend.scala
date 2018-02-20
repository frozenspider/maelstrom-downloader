package org.fs.mael.core

import java.net.URI

import org.fs.mael.core.entry.DownloadEntry

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

  // TODO: Rework
  /** Create a {@code DownloadEntry} from an URI */
  def create(uri: URI): DE = {
    require(isSupported(uri), "URI not supported")
    createInner(uri)
  }

  val downloader: BackendDownloader[DE]

  protected def createInner(uri: URI): DE

  //  def start(de: DE): Unit

  //  def stop(de: DE): Unit
}
