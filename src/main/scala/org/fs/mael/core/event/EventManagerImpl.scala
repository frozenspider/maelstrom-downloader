package org.fs.mael.core.event

import scala.collection.Seq
import scala.collection.mutable.PriorityQueue
import scala.math.Ordering

import org.slf4s.Logging

class EventManagerImpl extends EventManager with Logging {
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

  /** Whether event processing should be paused, needed for tests */
  @volatile
  private var paused: Boolean = false

  /** Event subscribers who will receive firing events, notified from worker thread */
  private var subscribers: Seq[EventSubscriber] = Seq.empty

  /** Priority queue for all non-processed events */
  private val pq: PriorityQueue[PriorityEvent] = new PriorityQueue()(peOrd)

  //
  // Client methods: subscription
  //

  override def subscribe(subscriber: EventSubscriber): Unit = {
    this.synchronized {
      subscribers = subscribers.filter(_.subscriberId != subscriber.subscriberId) :+ subscriber
    }
  }

  override def unsubscribe(id: String): Unit = {
    this.synchronized {
      subscribers = subscribers.filter(_.subscriberId != id)
    }
  }

  //
  // Client methods: events firing
  //

  override def fire(event: PriorityEvent): Unit = {
    this.synchronized {
      log.trace(event.msg)
      event.order = 1 + (if (lastAssignedOrder < Long.MaxValue) lastAssignedOrder else 0)
      pq.enqueue(event)
      lastAssignedOrder = event.order
    }
  }

  //
  // Test methods
  //

  /** For test usage only! */
  def test_getSubscribers = subscribers

  /** For test usage only! */
  def test_pause(): Unit = paused = true

  /** For test usage only! */
  def test_resume(): Unit = paused = false

  //
  // Helpers
  //

  private var lastAssignedOrder: Long = 0

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
            if (log.underlying.isTraceEnabled && !paused) {
              val copy = copyQueue()
              if (!copy.isEmpty) {
                log.trace(copy.size.toString + " events queued")
                copy.foreach { e =>
                  log.trace(" >" + e.toString)
                }
              }
            }

            if (!paused) {
              loop()
            }
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
          e match {
            case e: EventForUi      => subscribers collect { case s: UiSubscriber => s.fired(e) }
            case e: EventForBackend => subscribers collect { case s: BackendSubscriber => s.fired(e) }
          }
          log.trace("Event processed: " + e)
        }
      }
    }
    thread.setName("event-processing-thread")
    thread.setDaemon(true)
    thread.start()
    thread
  }
}
