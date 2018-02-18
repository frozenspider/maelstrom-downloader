package org.fs.mael.core.controller

import java.util.UUID

trait EventSubscriber {
  val id: String

  def added(de: DownloadEntry): Unit

  def removed(deId: UUID): Unit

  def error(de: DownloadEntry): Unit

  /** Download progress changed */
  def progress(de: DownloadEntry): Unit

  /** Any download entry properties change other than download progress */
  def updated(de: DownloadEntry): Unit

  override final def equals(obj: Any): Boolean = obj match {
    case es: EventSubscriber => this.id == es.id
    case _                   => false
  }

  override final val hashCode: Int = id.hashCode()
}
