package org.fs.mael.core

import java.net.URI

import scala.collection.SortedSet

import org.fs.mael.core.entry.DownloadEntryView

object BackendManager {
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

  def findFor(uri: URI): Option[Backend] = {
    this.synchronized {
      _backends map (_._1) find (_.isSupported(uri))
    }
  }

  def findFor(de: DownloadEntryView): BackendWithEntry = {
    this.synchronized {
      val backend = (_backends map (_._1) find (_.entryClass isInstance de)).get
      BackendWithEntry(backend, de)
    }
  }
}
