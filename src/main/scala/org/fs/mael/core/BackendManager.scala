package org.fs.mael.core

import java.net.URI

import scala.collection.SortedSet

object BackendManager {
  /** Processors, ordered from highest to lowest priority */
  private var _backends: SortedSet[Backend] =
    SortedSet.empty(Ordering.ordered[Backend].reverse)

  def addBackend(backend: Backend): Unit = {
    this.synchronized {
      _backends += backend
    }
  }

  def removeBackend(backend: Backend): Unit = {
    this.synchronized {
      _backends -= backend
    }
  }

  def get(uri: URI): Option[Backend] = {
    this.synchronized {
      _backends find (_.isSupported(uri))
    }
  }
}
