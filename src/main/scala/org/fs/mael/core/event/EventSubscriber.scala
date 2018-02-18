package org.fs.mael.core.event

import java.util.UUID

import org.fs.mael.core.controller.DownloadDetails

trait EventSubscriber {
  val id: String

  def added(de: DownloadDetails): Unit

  def removed(deId: UUID): Unit

  def error(de: DownloadDetails): Unit

  /** Download progress changed */
  def progress(de: DownloadDetails): Unit

  /** Any download entry properties change other than download progress */
  def updated(de: DownloadDetails): Unit

  override final def equals(obj: Any): Boolean = obj match {
    case es: EventSubscriber => this.id == es.id
    case _                   => false
  }

  override final val hashCode: Int = id.hashCode()
}
