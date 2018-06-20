package org.fs.mael.core.speed

import java.io.File
import java.net.URI

import scala.collection.immutable.SortedMap

import org.fs.mael.core.config.InMemoryConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventSubscriber
import org.fs.mael.core.event.Events
import org.fs.mael.core.event.Events._
import org.fs.mael.test.stub.StoringEventManager
import org.joda.time.DateTimeUtils
import org.junit.runner.RunWith
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class SpeedTrackerSpec
  extends FunSuite {

  //
  // Calculation tests
  //

  test("calculation - not enough info") {
    val tracker = getSimpleTracker()._1
    assert(tracker.calcSpeedOption(asMap(0L -> 0L)) === None)
    assert(tracker.calcSpeedOption(asMap(0L -> 0L, System.currentTimeMillis -> 100L)) === None)
  }

  test("calculation - uniform size growth") {
    val tracker = getSimpleTracker()._1
    val pairs = (0 to 100000 by 1000) map (i => (i.toLong -> i.toLong * 5))
    val map = asMap(pairs: _*)
    assert(tracker.calcSpeedOption(map) === Some(5000))
  }

  test("calculation - non-uniform size growth - 0 then even") {
    val tracker = getSimpleTracker()._1
    val pairs = (0 to 100000 by 1000) map {
      case i if i < 70000 => (i.toLong -> 0L)
      case i              => (i.toLong -> i.toLong * 5)
    }
    val map = asMap(pairs: _*)
    assert(tracker.calcSpeedOption(map) === Some(5000))
  }

  test("calculation - non-uniform size growth - 0 then full") {
    val tracker = getSimpleTracker()._1
    val pairs = (0 to 100000 by 1000) map {
      case i if i < 70000 => (i.toLong -> 0L)
      case i              => (i.toLong -> 500000L)
    }
    val map = asMap(pairs: _*)
    assert(tracker.calcSpeedOption(map) === Some(5000))
  }

  test("calculation - no size growth") {
    val tracker = getSimpleTracker()._1
    val pairs = (0 to 100000 by 1000) map (_.toLong -> 500000L)
    val map = asMap(pairs: _*)
    assert(tracker.calcSpeedOption(map) === Some(0))
  }

  test("calculation - size decreased") {
    val tracker = getSimpleTracker()._1
    val pairs = (0 to 100000 by 1000) map (i => (i.toLong -> (500000L - i)))
    val map = asMap(pairs: _*)
    assert(tracker.calcSpeedOption(map) === None)
  }

  //
  // Interaction tests
  //

  test("calculator does not subscribe to events") {
    var subscribed = false
    val eventMgr = new StoringEventManager {
      override def subscribe(subscriber: EventSubscriber): Unit = {
        subscribed = true
      }
    }
    val calc = new SpeedTrackerImpl(eventMgr, startBackgroundUpdateThread = false)
    assert(!subscribed)
  }

  test("status change events causes reset") {
    val (tracker, eventMgr) = getSimpleTracker()
    val de = getEmptyDownloadEntry()
    tracker.fired(Events.StatusChanged(de, de.status))
    assert(eventMgr.events === Seq(SpeedEta(de, None, None)))
  }

  test("progress events are processed properly") {
    val eventMgr = new StoringEventManager
    val tracker = new SpeedTrackerImpl(eventMgr, Int.MaxValue, false)
    val de = getEmptyDownloadEntry()

    DateTimeUtils.setCurrentMillisFixed(0)
    tracker.fired(Events.Progress(de))

    de.sections += (0L -> 100L)
    DateTimeUtils.setCurrentMillisFixed(50)
    tracker.fired(Events.Progress(de))

    de.sections += (0L -> 100L, 5000L -> 100L)
    DateTimeUtils.setCurrentMillisFixed(100)
    tracker.fired(Events.Progress(de))

    de.sections += (0L -> 200L, 5000L -> 200L)
    DateTimeUtils.setCurrentMillisFixed(150)
    tracker.fired(Events.Progress(de))

    de.sections += (0L -> 300L, 5000L -> 300L)
    DateTimeUtils.setCurrentMillisFixed(200)
    tracker.fired(Events.Progress(de))

    assert(eventMgr.events.size === 5)
    assert(eventMgr.events.forall(_.getClass == classOf[SpeedEta]))

    assert(eventMgr.events.last.asInstanceOf[SpeedEta].speedOption === Some(3000))
  }

  test("progress events filters out old data") {
    val eventMgr = new StoringEventManager
    val tracker = new SpeedTrackerImpl(eventMgr, 10, false)
    val de = getEmptyDownloadEntry()
    val startTime = getNow()

    (1 to 100) foreach { t =>
      DateTimeUtils.setCurrentMillisFixed(t)
      tracker.fired(Events.Progress(de))
    }

    DateTimeUtils.setCurrentMillisFixed(101)
    tracker.fired(Events.Progress(de))

    de.sections += (0L -> 200L)
    DateTimeUtils.setCurrentMillisFixed(102)
    tracker.fired(Events.Progress(de))

    assert(eventMgr.events.size === 102)
    assert(eventMgr.events.forall(_.getClass == classOf[SpeedEta]))

    assert(eventMgr.events.last.asInstanceOf[SpeedEta].speedOption === Some(20000)) // Changed by 200 in 10 ms
  }

  //
  // Utility
  //

  private def getSimpleTracker(): (SpeedTrackerImpl, StoringEventManager) = {
    val eventMgr = new StoringEventManager
    (new SpeedTrackerImpl(eventMgr, startBackgroundUpdateThread = false), eventMgr)
  }

  private def getEmptyDownloadEntry(): DownloadEntry =
    DownloadEntry("", new URI(""), new File(""), None, None, "", new InMemoryConfigStore)

  private def getNow() = System.currentTimeMillis

  private def asMap(s: (Long, Long)*): SortedMap[Long, Long] = {
    SortedMap(s.map(p => p._1.longValue -> p._2.longValue()): _*)
  }
}
