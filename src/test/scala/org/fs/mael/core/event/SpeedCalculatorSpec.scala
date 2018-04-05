package org.fs.mael.core.event

import java.io.File
import java.net.URI

import scala.collection.immutable.SortedMap

import org.fs.mael.core.config.InMemoryConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.Events._
import org.fs.mael.test.stub.StoringEventManager
import org.joda.time.DateTimeUtils
import org.junit.runner.RunWith
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class SpeedCalculatorSpec
  extends FunSuite {

  //
  // Calculation tests
  //

  test("calculation - not enough info") {
    val calc = getSimpleCalc()
    assert(calc.test_calcSpeed(asMap(0L -> 0L)) === None)
    assert(calc.test_calcSpeed(asMap(0L -> 0L, System.currentTimeMillis -> 100L)) === None)
  }

  test("calculation - uniform size growth") {
    val calc = getSimpleCalc()
    val pairs = (0 to 100000 by 1000) map (i => (i.toLong -> i.toLong * 5))
    val map = asMap(pairs: _*)
    assert(calc.test_calcSpeed(map) === Some(5000))
  }

  test("calculation - non-uniform size growth - 0 then even") {
    val calc = getSimpleCalc()
    val pairs = (0 to 100000 by 1000) map {
      case i if i < 70000 => (i.toLong -> 0L)
      case i              => (i.toLong -> i.toLong * 5)
    }
    val map = asMap(pairs: _*)
    assert(calc.test_calcSpeed(map) === Some(5000))
  }

  test("calculation - non-uniform size growth - 0 then full") {
    val calc = getSimpleCalc()
    val pairs = (0 to 100000 by 1000) map {
      case i if i < 70000 => (i.toLong -> 0L)
      case i              => (i.toLong -> 500000L)
    }
    val map = asMap(pairs: _*)
    assert(calc.test_calcSpeed(map) === Some(5000))
  }

  test("calculation - no size growth") {
    val calc = getSimpleCalc()
    val pairs = (0 to 100000 by 1000) map (_.toLong -> 500000L)
    val map = asMap(pairs: _*)
    assert(calc.test_calcSpeed(map) === Some(0))
  }

  test("calculation - size decreased") {
    val calc = getSimpleCalc()
    val pairs = (0 to 100000 by 1000) map (i => (i.toLong -> (500000L - i)))
    val map = asMap(pairs: _*)
    assert(calc.test_calcSpeed(map) === None)
  }

  //
  // Interaction tests
  //

  test("calculator subscribes to events") {
    var subscribed = false
    val eventMgr = new StoringEventManager {
      override def subscribe(subscriber: EventSubscriber): Unit = {
        subscribed = true
      }
    }
    val calc = new SpeedCalculator(eventMgr)
    assert(subscribed)
  }

  test("status change events causes reset") {
    val calc = getSimpleCalc()
    val de = getEmptyDownloadEntry()
    de.speedOption = Some(123456)
    calc.fired(Events.StatusChanged(de, de.status))
    assert(!de.speedOption.isDefined)
  }

  test("progress events are processed properly") {
    val eventMgr = new StoringEventManager
    val calc = new SpeedCalculator(eventMgr, Int.MaxValue)
    val de = getEmptyDownloadEntry()

    DateTimeUtils.setCurrentMillisFixed(0)
    calc.fired(Events.Progress(de))

    de.sections += (0L -> 100L)
    DateTimeUtils.setCurrentMillisFixed(50)
    calc.fired(Events.Progress(de))

    de.sections += (0L -> 100L, 5000L -> 100L)
    DateTimeUtils.setCurrentMillisFixed(100)
    calc.fired(Events.Progress(de))

    de.sections += (0L -> 200L, 5000L -> 200L)
    DateTimeUtils.setCurrentMillisFixed(150)
    calc.fired(Events.Progress(de))

    de.sections += (0L -> 300L, 5000L -> 300L)
    DateTimeUtils.setCurrentMillisFixed(200)
    calc.fired(Events.Progress(de))

    assert(eventMgr.events === Seq.fill(5)(Events.Speed(de)))

    assert(de.speedOption.isDefined)
    assert(de.speedOption.get === 3000)
  }

  test("progress events filters out old data") {
    val eventMgr = new StoringEventManager
    val calc = new SpeedCalculator(eventMgr, 10)
    val de = getEmptyDownloadEntry()
    val startTime = getNow()

    (1 to 100) foreach { t =>
      DateTimeUtils.setCurrentMillisFixed(t)
      calc.fired(Events.Progress(de))
    }

    DateTimeUtils.setCurrentMillisFixed(101)
    calc.fired(Events.Progress(de))

    de.sections += (0L -> 200L)
    DateTimeUtils.setCurrentMillisFixed(102)
    calc.fired(Events.Progress(de))

    assert(eventMgr.events.size === 102)
    assert(eventMgr.events === Seq.fill(102)(Events.Speed(de)))

    assert(de.speedOption.isDefined)
    assert(de.speedOption.get === 20000) // Changed by 200 in 10 ms
  }

  //
  // Utility
  //

  private def getSimpleCalc(): SpeedCalculator =
    new SpeedCalculator(new StoringEventManager)

  private def getEmptyDownloadEntry(): DownloadEntry =
    DownloadEntry("", new URI(""), new File(""), None, None, "", new InMemoryConfigStore)

  private def getNow() = System.currentTimeMillis

  private def asMap(s: (Long, Long)*): SortedMap[Long, Long] = {
    SortedMap(s.map(p => p._1.longValue -> p._2.longValue()): _*)
  }
}
