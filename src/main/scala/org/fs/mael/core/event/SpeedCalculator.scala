package org.fs.mael.core.event

import org.fs.mael.core.entry.DownloadEntry
import scala.collection.mutable.WeakHashMap
import scala.collection.SortedMap

/**
 * Keeps track and calculates the download speed, updating the cached speed value in download entry.
 * Note that this by itself generates no event.
 *
 * @author FS
 */
class SpeedCalculator { self =>
  /** Time during which downloaded chunk sizes are accumulated and accounted for speed calculation */
  private val bufferMs = 3000

  private val whm = new WeakHashMap[DownloadEntry, SortedMap[Long, Long]]

  def update(de: DownloadEntry): Unit = this.synchronized {
    val oldV = whm.getOrElse(de, SortedMap.empty[Long, Long])
    val now = System.currentTimeMillis()
    val newV = oldV.filterKeys(_ >= (now - bufferMs)) + (now -> de.downloadedSize)
    de.speedOption = calcSpeed(newV)
    whm.put(de, newV)
  }

  private def calcSpeed(entries: SortedMap[Long, Long]): Option[Long] = {
    if (entries.size > 1) {
      val seq = entries.toIndexedSeq
      // Do the actual speed calculation
      val (firstT, firstS) = seq.head
      val (lastT, lastS) = seq.last
      val diffS = lastS - firstS
      val diffT = lastT - firstT
      if (diffS > 0) {
        Some(diffS * 1000 / diffT)
      } else {
        // Can happen if e.g. download was restarted
        None
      }
    } else {
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
          map.foreach {
            case (de, entries) => self.synchronized {
              de.speedOption = calcSpeed(entries)
            }
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