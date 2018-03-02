package org.fs.mael.core.event

import java.util.UUID

import scala.collection.Seq
import scala.collection.mutable.PriorityQueue
import scala.math.Ordering

import org.fs.mael.core.Status
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.slf4s.Logging

class EventManager extends Logging {
  /** "Greatest" event has highest priority, tiebreaker - lowest order */
  private val peOrd: Ordering[PriorityEvent] = new Ordering[PriorityEvent] {
    override def compare(x: PriorityEvent, y: PriorityEvent): Int = {
      val cmp1 = (x.priority compare y.priority)
      if (cmp1 != 0)
        cmp1
      else
        -(x.order compare y.order)
    }
  }

  /** Event subscribers who will receive firing events, notified from worker thread */
  private var subscribers: Set[EventSubscriber] = Set.empty

  /** Priority queue for all non-processed events */
  private val pq: PriorityQueue[PriorityEvent] = new PriorityQueue()(peOrd)

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

  def fireAdded(de: DownloadEntryView): Unit = {
    enqueue(event.Added(
      "added " + de.uri,
      () => subscribers collect { case ui: UiSubscriber => ui.added(de) }
    ))
  }

  def fireRemoved(de: DownloadEntryView): Unit = {
    enqueue(event.Removed(
      "removed " + de.uri,
      () => subscribers collect { case ui: UiSubscriber => ui.removed(de) }
    ))
  }

  def fireStatusChanged(de: DownloadEntryView, prevStatus: Status): Unit = {
    enqueue(event.StatusChanged(
      "status of " + de.uri + " changed from " + prevStatus + " to " + de.status,
      () => subscribers collect { case ui: UiSubscriber => ui.statusChanged(de, prevStatus) }
    ))
  }

  /** Download progress changed */
  def fireProgress(de: DownloadEntryView): Unit = {
    enqueue(event.Progress(
      "progress",
      () => subscribers collect { case ui: UiSubscriber => ui.progress(de) }
    ))
  }

  /** Any displayed download detail (other than download progress) changed */
  def fireDetailsChanged(de: DownloadEntryView): Unit = {
    enqueue(event.DetailsChanged(
      "details changed",
      () => subscribers collect { case ui: UiSubscriber => ui.detailsChanged(de) }
    ))
  }

  def fireLogged(de: DownloadEntryView, entry: LogEntry): Unit = {
    enqueue(event.Logged(
      "logged",
      () => subscribers collect { case ui: UiSubscriber => ui.logged(de, entry) }
    ))
  }

  /** Download entry configuration changed */
  def fireConfigChanged(de: DownloadEntry[_]): Unit = {
    enqueue(event.ConfigChanged(
      "config changed",
      () => subscribers collect { case bck: BackendSubscriber => bck.configChanged(de) }
    ))
  }

  //
  // Helpers
  //

  private var lastAssignedOrder: Long = 0

  private def enqueue(event: PriorityEvent): Unit = {
    this.synchronized {
      log.trace(event.msg)
      event.order = 1 + (if (lastAssignedOrder < Long.MaxValue) lastAssignedOrder else 0)
      pq.enqueue(event)
      lastAssignedOrder = event.order
    }
  }

  private def dequeueAll(): Seq[PriorityEvent] = {
    // Avoid unnecessary synchronization
    if (pq.isEmpty) {
      Seq.empty
    } else {
      this.synchronized {
        pq.dequeueAll
      }
    }
  }

  private def copyQueue(): Seq[PriorityEvent] = {
    this.synchronized {
      pq.clone().dequeueAll
    }
  }

  private val eventProcessingThread: Thread = {
    val thread = new Thread {
      override def run(): Unit = {
        while (true) {
          try {
            if (log.underlying.isTraceEnabled) {
              val copy = copyQueue()
              if (!copy.isEmpty) {
                log.trace(copy.size.toString + " events queued")
                copy.foreach { e =>
                  log.trace(" >" + e.toString)
                }
              }
            }

            loop()
            Thread.sleep(20)
          } catch {
            case ex: Exception =>
              log.error("Error in worker thread!", ex)
          }
        }
      }

      def loop(): Unit = {
        val events = dequeueAll()
        events foreach { e =>
          e.eventFunc.apply()
          log.trace("Event processed: " + e)
        }
      }
    }
    thread.setName("event-processing-thread")
    thread.setDaemon(true)
    thread.start()
    thread
  }

  private sealed abstract class PriorityEvent(val priority: Int) {
    var order: Long = -1
    val msg: String
    val eventFunc: () => Unit

    override def toString(): String = {
      val fullName = this.getClass.getName
      val lastSepIdx = fullName.lastIndexWhere(c => c == '.' || c == '$')
      s"${fullName.drop(lastSepIdx + 1)}($priority, $order, $msg)"
    }
  }

  private object event {
    case class ConfigChanged(msg: String, eventFunc: () => Unit)
      extends PriorityEvent(Int.MaxValue)

    case class Added(msg: String, eventFunc: () => Unit)
      extends PriorityEvent(100)

    case class Removed(msg: String, eventFunc: () => Unit)
      extends PriorityEvent(100)

    case class StatusChanged(msg: String, eventFunc: () => Unit)
      extends PriorityEvent(100)

    case class DetailsChanged(msg: String, eventFunc: () => Unit)
      extends PriorityEvent(50)

    case class Logged(msg: String, eventFunc: () => Unit)
      extends PriorityEvent(20)

    case class Progress(msg: String, eventFunc: () => Unit)
      extends PriorityEvent(Int.MinValue)
  }
}
