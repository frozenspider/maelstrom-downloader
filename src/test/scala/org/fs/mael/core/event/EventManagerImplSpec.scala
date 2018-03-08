package org.fs.mael.core.event

import java.io.File
import java.net.URI

import org.fs.mael.core.Status
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.Events._
import org.fs.mael.test.stub.StubBackend
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

import com.github.nscala_time.time.Imports._

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class EventManagerImplSpec
  extends FunSuite
  with BeforeAndAfter {

  private val eventMgr = new EventManagerImpl

  private var firedEvents: IndexedSeq[PriorityEvent] = _

  private val uiSubscriber1 = new TestUiSubscriber("ui1")
  private val uiSubscriber2 = new TestUiSubscriber("ui2")
  private val backendSubscriber1 = new TestBackendSubscriber("backend1")

  private val expectedSubscribers = Set(uiSubscriber1, uiSubscriber2, backendSubscriber1)

  before {
    eventMgr.test_getSubscribers.foreach { s =>
      eventMgr.unsubscribe(s.subscriberId)
    }
    eventMgr.subscribe(uiSubscriber1)
    eventMgr.subscribe(uiSubscriber2)
    eventMgr.subscribe(backendSubscriber1)
    firedEvents = IndexedSeq.empty
  }

  test("subscribers") {
    assert(eventMgr.test_getSubscribers === expectedSubscribers)
  }

  test("subscribing twice does nothing") {
    eventMgr.subscribe(uiSubscriber1)
    eventMgr.subscribe(backendSubscriber1)
    assert(eventMgr.test_getSubscribers === expectedSubscribers)
  }

  test("subscribing with duplicate ID does nothing") {
    eventMgr.subscribe(new TestBackendSubscriber("ui1"))
    assert(eventMgr.test_getSubscribers === expectedSubscribers)
  }

  test("unsubscribing") {
    eventMgr.unsubscribe(uiSubscriber2.subscriberId)
    assert(eventMgr.test_getSubscribers === Set(uiSubscriber1, backendSubscriber1))
    expectedSubscribers.foreach { s =>
      eventMgr.unsubscribe(s.subscriberId)
    }
    assert(eventMgr.test_getSubscribers.isEmpty)
  }

  test("events priority") {
    val stubBackend = new StubBackend
    val now = DateTime.now()
    val de = stubBackend.create(new URI("some-uri"), new File(""), None, None, "")
    eventMgr.test_pause()
    eventMgr.fireProgress(de) // 1
    eventMgr.fireDetailsChanged(de) // 2
    eventMgr.fireAdded(de) // 3
    eventMgr.fireStatusChanged(de, Status.Error) // 4
    eventMgr.fireRemoved(de) // 5
    eventMgr.fireLogged(de, LogEntry.info(now, "Ha-ha-ha!")) // 6
    eventMgr.fireConfigChanged(de) // 7
    assert(firedEvents.size === 0)
    eventMgr.test_resume()
    Thread.sleep(100)
    assert(firedEvents.size ===
      // 2 UI subscribers, 6 UI events
      // 1 backend subscriber, 1 backend event
      (2 * 6 + 1 * 1))

    // Let's construct the expected sequence of events manually and compare them
    val expectedEventsRaw: Seq[PriorityEvent] = Seq(
      Progress(de),
      DetailsChanged(de),
      Added(de),
      StatusChanged(de, Status.Error),
      Removed(de),
      Logged(de, LogEntry.info(now, "Ha-ha-ha!")),
      ConfigChanged(de)
    ).zipWithIndex.map { case (e, idx) => e.order = idx + 1; e }
    val expectedEvents: Seq[PriorityEvent] = expectedEventsRaw.flatMap {
      case e: EventForUi => Seq.fill(2)(e)
      case e             => Seq(e)
    }.sortBy(_.priority.toLong.unary_-)
    expectedEvents.zipWithIndex.foreach {
      case (e, idx) =>
        assert(firedEvents(idx) === e)
        assert(firedEvents(idx).order === e.order)
    }
  }

  private class TestUiSubscriber(override val subscriberId: String) extends UiSubscriber {
    override def fired(event: EventForUi): Unit =
      firedEvents = firedEvents :+ event.asInstanceOf[PriorityEvent]
  }

  private class TestBackendSubscriber(override val subscriberId: String) extends BackendSubscriber {
    override def fired(event: EventForBackend): Unit =
      firedEvents = firedEvents :+ event.asInstanceOf[PriorityEvent]
  }
}
