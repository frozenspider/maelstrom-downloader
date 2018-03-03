package org.fs.mael.test.stub

import org.fs.mael.core.transfer.TransferManager
import java.io.InputStream
import org.scalatest.Assertions._

class ControlledTransferManager extends TransferManager {
  @volatile private var started = false

  override def read(is: InputStream, buffer: Array[Byte]): Int = this.synchronized {
    if (!started) {
      fail("Unsanctioned read!")
    }
    is.read(buffer)
  }

  def start(): Unit = this.synchronized {
    started = true
  }

  def reset(): Unit = this.synchronized {
    started = false
  }
}
