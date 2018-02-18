package org.fs.mael.core.event

import java.util.UUID

import scala.collection.Seq
import scala.collection.mutable.PriorityQueue
import scala.math.Ordering

import org.fs.mael.core.controller.DownloadEntry
import org.fs.mael.core.controller.LogEntry
import org.fs.mael.core.controller.view.DownloadDetailsView
import org.slf4s.Logging

object EventManager extends Logging {
  private type PriorityEvent = (Int, () => Unit)
  private implicit val peOrd: Ordering[PriorityEvent] = new Ordering[PriorityEvent] {
    def compare(x: PriorityEvent, y: PriorityEvent) = x._1 compare y._1
  }

  private object priority {
    val High = Int.MaxValue
    val Low = Int.MinValue
  }

  /** Event subscribers who will receive firing events, notified from worker thread */
  private var subscribers: Set[EventSubscriber] = Set.empty

  /** Priority queue for all non-processed events */
  private val pq: PriorityQueue[PriorityEvent] = new PriorityQueue

  //
  // Client methods: subscription
  //

  def subscribe(subscriber: EventSubscriber): Unit = {
    this.synchronized {
      subscribers += subscriber
    }
  }

  def unsubscribe(id: String): Unit = {
    this.synchronized {
      subscribers = subscribers.filter(_.subscriberId != id)
    }
  }

  //
  // Client methods: events firing
  //

  def fireAdded(dd: DownloadDetailsView): Unit = {
    enqueue(
      priority.High,
      subscribers collect { case ui: UiSubscriber => ui.added(dd) }
    )
  }

  def fireRemoved(ddId: UUID): Unit = {
    enqueue(
      priority.High,
      subscribers collect { case ui: UiSubscriber => ui.removed(ddId) }
    )
  }

  def fireError(dd: DownloadDetailsView): Unit = {
    enqueue(
      priority.High,
      subscribers collect { case ui: UiSubscriber => ui.error(dd) }
    )
  }

  /** Download progress changed */
  def fireProgress(dd: DownloadDetailsView): Unit = {
    enqueue(
      priority.Low,
      subscribers collect { case ui: UiSubscriber => ui.progress(dd) }
    )
  }

  /** Any displayed download detail (other than download progress) changed */
  def fireDetails(dd: DownloadDetailsView): Unit = {
    enqueue(
      priority.High,
      subscribers collect { case ui: UiSubscriber => ui.details(dd) }
    )
  }

  def fireLogged(dd: DownloadDetailsView, entry: LogEntry): Unit = {
    enqueue(
      priority.High,
      subscribers collect { case ui: UiSubscriber => ui.logged(dd, entry) }
    )
  }

  /** Any other Download progress changed */
  def fireConfigChanged(de: DownloadEntry): Unit = {
    enqueue(
      priority.High,
      subscribers collect { case bck: BackendSubscriber => bck.configChanged(de) }
    )
  }

  //
  // Helpers
  //

  private def enqueue(priority: Int, event: => Unit): Unit = {
    this.synchronized {
      pq.enqueue(
        ((priority, () => event))
      )
    }
  }

  private def dequeueAll(): Seq[() => Unit] = {
    // Avoid unnecessary synchronization
    if (pq.isEmpty) {
      Seq.empty
    } else {
      this.synchronized {
        pq.dequeueAll
      }.map(_._2)
    }
  }

  private val eventProcessingThread: Thread = {
    val thread = new Thread {
      override def run(): Unit = {
        while (true) {
          try {
            loop()
            Thread.sleep(10)
          } catch {
            case ex: Exception =>
              log.error("Error in worker thread!", ex)
          }
        }
      }

      def loop(): Unit = {
        val functions = dequeueAll()
        functions foreach (_.apply())
      }
    }
    thread.setName("event-processing-thread")
    thread.setDaemon(true)
    thread.start()
    thread
  }
}
