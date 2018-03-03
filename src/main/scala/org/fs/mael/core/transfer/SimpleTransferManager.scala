package org.fs.mael.core.transfer

import java.io.InputStream

class SimpleTransferManager extends TransferManager {
  def read(is: InputStream, buffer: Array[Byte]): Int = is.read(buffer)
}
