package org.fs.mael.core.speed

import scala.collection.SortedMap
import scala.collection.mutable.WeakHashMap

import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.event.UiSubscriber

import com.github.nscala_time.time.Imports._

/**
 * Keeps track and calculates the download speed, updating the cached speed value in download entry.
 * Fires `Speed` event.
 *
 * @author FS
 */
class SpeedTrackerImpl(
  eventMgr: EventManager,
  /** Time during which downloaded chunk sizes are accumulated and accounted for speed calculation */
  bufferMs: Int = 3000
) extends SpeedTracker with UiSubscriber { self =>
  private type Timestamp = Long
  private type CurrentSize = Long

  override val subscriberId = "speed-tracker"

  private val whm = new WeakHashMap[DownloadEntry, SortedMap[Timestamp, CurrentSize]]

  override def reset(de: DownloadEntry): Unit = this.synchronized {
    whm.put(de, SortedMap.empty[Timestamp, CurrentSize])
    eventMgr.fireSpeedEta(de, None, None)
  }

  // TODO: Use something like exponential moving average
  // or https://stackoverflow.com/questions/2779600/how-to-estimate-download-time-remaining-accurately
  override def update(de: DownloadEntry): Unit = this.synchronized {
    val oldV = whm.getOrElse(de, SortedMap.empty[Timestamp, CurrentSize])
    val now = DateTime.now().getMillis
    val newV = oldV.filterKeys(_ >= (now - bufferMs)) + (now -> de.downloadedSize)
    whm.put(de, newV)
    val speedOption = calcSpeedOption(newV)
    val etaOption = calcEtaOption(de, speedOption)
    eventMgr.fireSpeedEta(de, speedOption, etaOption)
  }

  def calcSpeedOption(entries: SortedMap[Timestamp, CurrentSize]): Option[Long] = {
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

  def calcEtaOption(de: DownloadEntry, speedOption: Option[Long]): Option[Long] = {
    (de.sizeOption, speedOption) match {
      case (Some(totalSize), Some(speed)) if speed > 1000 =>
        val remaining = totalSize - de.downloadedSize
        Some(remaining / speed)
      case _ =>
        None
    }
  }

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
