package org.fs.mael.core.event

import java.util.UUID

import org.fs.mael.core.controller.DownloadDetailsView
import org.fs.mael.core.controller.DownloadEntry

trait EventSubscriber {
  val subscriberId: String

  def added(dd: DownloadDetailsView): Unit

  def removed(ddId: UUID): Unit

  def error(dd: DownloadDetailsView): Unit

  /** Download progress changed */
  def progress(dd: DownloadDetailsView): Unit

  /** Any download entry properties change other than download progress */
  def updated(de: DownloadEntry): Unit

  override final def equals(obj: Any): Boolean = obj match {
    case es: EventSubscriber => this.subscriberId == es.subscriberId
    case _                   => false
  }

  override final val hashCode: Int = subscriberId.hashCode()
}
