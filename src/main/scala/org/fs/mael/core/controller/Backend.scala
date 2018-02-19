package org.fs.mael.core.controller

import java.net.URI

/*
 * Backend needs to know
 * - backend-specific download properties
 * UI needs to query backend for
 * - backend-specific UI
 */
trait Backend extends Comparable[Backend] {
  type DE <: DownloadEntry

  val priority: Int

  val id: String

  def isSupported(uri: URI): Boolean

  // TODO: Rework
  /** Create a {@code DownloadEntry} from an URI */
  def create(uri: URI): DE = {
    require(isSupported(uri), "URI not supported")
    createInner(uri)
  }

  protected def createInner(uri: URI): DE

  //  def start(de: DE): Unit

  //  def stop(de: DE): Unit

  override def compareTo(that: Backend): Int =
    this.priority compare that.priority
}
