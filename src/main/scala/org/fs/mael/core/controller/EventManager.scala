package org.fs.mael.core.controller

import java.util.UUID

object EventManager {
  private var subscribers: Set[EventSubscriber] = Set.empty

  def subscribe(subscriber: EventSubscriber): Unit = {
    subscribers += subscriber
  }

  def unsubscribe(id: String): Unit = {
    subscribers = subscribers.filter(_.id != id)
  }

  def fireAdded(de: DownloadEntry): Unit = {
    subscribers foreach (_.added(de))
  }

  def fireRemoved(deId: UUID): Unit = {
    subscribers foreach (_.removed(deId))
  }

  def fireError(de: DownloadEntry): Unit = {
    subscribers foreach (_.error(de))
  }

  /** Download progress changed */
  def fireProgress(de: DownloadEntry): Unit = {
    subscribers foreach (_.progress(de))
  }

  /** Any other Download progress changed */
  def fireUpdated(de: DownloadEntry): Unit = {
    subscribers foreach (_.updated(de))
  }
}
