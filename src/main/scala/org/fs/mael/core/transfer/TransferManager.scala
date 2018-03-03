package org.fs.mael.core.transfer

import java.io.InputStream

/**
 * Manages data transfers over the network.
 *
 * Must be used by all backends for data streaming.
 *
 * @author FS
 */
trait TransferManager {
  def read(is: InputStream, buffer: Array[Byte]): Int
}
