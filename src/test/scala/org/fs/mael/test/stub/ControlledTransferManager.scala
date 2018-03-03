package org.fs.mael.test.stub

import org.fs.mael.core.transfer.TransferManager
import java.io.InputStream
import org.scalatest.Assertions._

class ControlledTransferManager extends TransferManager {
  @volatile private var started = false
  @volatile var bytesRead: Int = 0
  @volatile var bytesAllowed: Int = Int.MaxValue

  override def read(is: InputStream, buffer: Array[Byte]): Int = this.synchronized {
    if (!started) {
      fail("Unsanctioned read!")
    }
    if (bytesAllowed == 0) {
      Thread.sleep(1000 * 1000)
      throw new IllegalStateException("Hey, wait a minute, test should've terminated already!")
    } else {
      val maxToRead = math.min(buffer.length, bytesAllowed)
      val len = is.read(buffer, 0, maxToRead)
      if (len > 0) {
        bytesRead += len
        bytesAllowed -= len
      }
      len
    }
  }

  def throttleBytes(bs: Int): Unit = {
    bytesAllowed = bs
  }

  def start(): Unit = this.synchronized {
    started = true
  }

  def reset(): Unit = this.synchronized {
    started = false
    bytesRead = 0
    bytesAllowed = Int.MaxValue
  }
}
