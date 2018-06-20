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
  private type Speed = Long

  private val whmSizes = new WeakHashMap[DownloadEntry, SortedMap[Timestamp, CurrentSize]]
  private val whmSpeeds = new WeakHashMap[DownloadEntry, SortedMap[Timestamp, Speed]]

  override val subscriberId = "speed-tracker"

  override def reset(de: DownloadEntry): Unit = this.synchronized {
    whmSizes.put(de, SortedMap.empty[Timestamp, CurrentSize])
    whmSpeeds.put(de, SortedMap.empty[Timestamp, Speed])
    eventMgr.fireSpeedEta(de, None, None)
  }

  override def update(de: DownloadEntry): Unit = this.synchronized {
    val now = DateTime.now().getMillis

    val oldSizes = whmSizes.getOrElse(de, SortedMap.empty[Timestamp, CurrentSize]).filterKeys(_ >= (now - bufferMs))
    val newSizes = oldSizes + (now -> de.downloadedSize)
    whmSizes.put(de, newSizes)

    val speedOption = calcSpeedOption(newSizes)
    val oldSpeeds = whmSpeeds.getOrElse(de, SortedMap.empty[Timestamp, Speed]).filterKeys(_ >= (now - bufferMs))
    val newSpeeds = oldSpeeds + (now -> speedOption.getOrElse(0L))
    whmSpeeds.put(de, newSpeeds)

    val etaOption = calcEtaOption(de, newSpeeds)
    eventMgr.fireSpeedEta(de, speedOption, etaOption)
  }

  def calcSpeedOption(entries: SortedMap[Timestamp, CurrentSize]): Option[Speed] = {
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

  def calcEtaOption(de: DownloadEntry, speeds: SortedMap[Timestamp, Speed]): Option[Long] = {
    val speeds2 = speeds.map(_._2).toSeq.dropWhile(_ == 0)
    if (speeds2.size <= 2) {
      None
    } else {
      // Exponential moving average
      val a = 0.3 // Smoothing factor
      val exponentiallySmoothedSpeed = speeds2.tail.foldLeft(speeds2.head.toDouble) {
        case (prev, curr) => a * curr + (1 - a) * prev
      }
      (de.sizeOption, exponentiallySmoothedSpeed) match {
        case (Some(totalSize), speed) if speed > 1000 =>
          val remaining = totalSize - de.downloadedSize
          Some(remaining / speed.toLong)
        case _ =>
          None
      }
    }
  }

  /** Thread which updates download speed every second */
  private val speedUpdateThread: Thread = {
    val thread = new Thread {
      override def run(): Unit = {
        while (true) {
          val map = self.synchronized {
            whmSizes.toMap
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
