package org.fs.mael.test.stub

import org.fs.mael.core.transfer.TransferManager
import java.io.InputStream
import org.scalatest.Assertions._

class ControlledTransferManager extends TransferManager {
  @volatile private var bytesAllowed: Int = Int.MaxValue
  @volatile var bytesRead: Int = 0

  override def read(is: InputStream, buffer: Array[Byte]): Int = this.synchronized {
    if (bytesAllowed == 0) {
      this.wait(20 * 1000)
      read(is, buffer)
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

  def throttleBytes(bs: Int): Unit = this.synchronized {
    bytesAllowed = bs
    this.notifyAll()
  }

  def reset(): Unit = this.synchronized {
    bytesRead = 0
    bytesAllowed = Int.MaxValue
  }
}
