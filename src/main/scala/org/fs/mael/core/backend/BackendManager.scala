package org.fs.mael.core.backend

import java.net.URI

import scala.collection.SortedSet

class BackendManager {
  /** Backends with priority, ordered from highest to lowest priority */
  private var _backends: SortedSet[(Backend, Int)] =
    SortedSet.empty((x, y) => -(x._2 compareTo y._2))

  def +=(backend: Backend, priority: Int): Unit = {
    this.synchronized {
      _backends += (backend -> priority)
    }
  }

  def -=(backend: Backend): Unit = {
    this.synchronized {
      _backends = _backends.filter(_._1 != backend)
    }
  }

  def list: Seq[Backend] = {
    _backends.toSeq.map(_._1)
  }

  def apply(id: String): Backend = {
    this.synchronized {
      (_backends map (_._1) find (_.id == id)).getOrElse {
        throw new IllegalArgumentException(s"No backend registered with id '$id'")
      }
    }
  }

  def findFor(uri: URI): Option[Backend] = {
    this.synchronized {
      _backends map (_._1) find (_.isSupported(uri))
    }
  }
}
