package org.fs.mael.backend.http

import org.fs.mael.core.BackendDownloader
import org.slf4s.Logging
import org.fs.mael.core.Status
import org.fs.mael.core.event.EventManager

class HttpBackendDownloader extends BackendDownloader[HttpBackend.DE] with Logging {

  private val dlThreadGroup = new ThreadGroup(HttpBackend.Id + "-dl")

  private var threads: Seq[DownloadingThread] = Seq.empty

  def startInner(de: HttpBackend.DE): Unit =
    this.synchronized {
      if (threads exists (t => t.de == de && t.isAlive)) {
        log.warn(s"Attempt to start an already-running entry: ${de.id} (${de.uri})")
      } else {
        val prevStatus = de.status
        de.status = Status.Running
        val newThread = new DownloadingThread(de)
        threads = newThread +: threads
        newThread.start()
        EventManager.fireStatusChanged(de, prevStatus)
      }
    }

  def stopInner(de: HttpBackend.DE): Unit =
    this.synchronized {
      threads find (_.de == de) match {
        case Some(t) =>
          val prevStatus = de.status
          de.status = Status.Stopped
          t.interrupt()
          threads = threads filter (_.de != de)
          EventManager.fireStatusChanged(de, prevStatus)
        case None =>
          log.warn(s"Attempt to stop a not started entry: ${de.id} (${de.uri})")
      }
    }

  private class DownloadingThread(val de: HttpBackend#DE)
    extends Thread(dlThreadGroup, dlThreadGroup.getName + "_" + de.id) {

    override def run(): Unit = {
      // TODO: HTTP code here
      ???
    }
  }

}
