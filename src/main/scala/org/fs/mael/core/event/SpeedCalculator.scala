package org.fs.mael.core.event

import scala.collection.SortedMap
import scala.collection.mutable.WeakHashMap

import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.Events._

import com.github.nscala_time.time.Imports._

/**
 * Keeps track and calculates the download speed, updating the cached speed value in download entry.
 * Fires `Speed` event.
 *
 * @author FS
 */
class SpeedCalculator(eventMgr: EventManager, bufferMs: Int = 3000) extends UiSubscriber { self =>
  private type Timestamp = Long
  private type CurrentSize = Long

  override val subscriberId = "speed-calculator"

  /** Time during which downloaded chunk sizes are accumulated and accounted for speed calculation */
  private val _bufferMs = bufferMs

  private val whm = new WeakHashMap[DownloadEntry, SortedMap[Timestamp, CurrentSize]]

  eventMgr.subscribe(this)

  override def fired(event: EventForUi): Unit = event match {
    case StatusChanged(de, _) => reset(de)
    case Progress(de)         => update(de)
    case _                    => // NOOP
  }

  private def reset(de: DownloadEntry): Unit = this.synchronized {
    de.speedOption = None
    whm.put(de, SortedMap.empty[Timestamp, CurrentSize])
    eventMgr.fireSpeed(de)
  }

  private def update(de: DownloadEntry): Unit = this.synchronized {
    val oldV = whm.getOrElse(de, SortedMap.empty[Timestamp, CurrentSize])
    val now = DateTime.now().getMillis
    val newV = oldV.filterKeys(_ >= (now - bufferMs)) + (now -> de.downloadedSize)
    de.speedOption = calcSpeed(newV)
    whm.put(de, newV)
    eventMgr.fireSpeed(de)
  }

  private def calcSpeed(entries: SortedMap[Timestamp, CurrentSize]): Option[Long] = {
    if (entries.size <= 3) {
      // No point in calculations, it would be very inaccurate anyway
      None
    } else {
      val seq = entries.toIndexedSeq
      // Do the actual speed calculation
      val (firstT, firstS) = seq.head
      val (lastT, lastS) = seq.last
      val diffS = lastS - firstS
      val diffT = lastT - firstT
      if (diffS >= 0) {
        Some(diffS * 1000 / diffT)
      } else {
        // Can happen e.g. if download was restarted
        None
      }
    }
  }

  /** For test usage only! */
  def test_calcSpeed(entries: SortedMap[Timestamp, CurrentSize]): Option[Long] = calcSpeed(entries)

  /** Thread which updates download speed every second */
  private val speedUpdateThread: Thread = {
    val thread = new Thread {
      override def run(): Unit = {
        while (true) {
          val map = self.synchronized {
            whm.toMap
          }
          map.keys.foreach { de =>
            update(de)
          }
          Thread.sleep(1000)
        }
      }
    }
    thread.setName("speed-calculator-thread")
    thread.setDaemon(true)
    thread.start()
    thread
  }
}
