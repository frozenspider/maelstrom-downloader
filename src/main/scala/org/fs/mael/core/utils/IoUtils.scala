package org.fs.mael.core.utils

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

import org.apache.http.util.ByteArrayBuffer
import org.fs.mael.core.utils.CoreUtils._

trait IoUtils {

  def shortToInt(s: Short): Int = {
    if (s >= 0) s else 65536 + s
  }

  def intToShort(i: Int): Short = {
    require(i >= 0 && i < 65536)
    if (i <= 32767) i.toShort else (i - 65536).toShort
  }

  def byteToInt(b: Byte): Int = {
    if (b >= 0) b else 256 + b
  }

  def intToByte(i: Int): Byte = {
    require(i >= 0 && i < 256)
    if (i <= 127) i.toByte else (i - 256).toByte
  }

  /**
   * Move a {@code from} file to {@code to} file, which shouldn't exist yet.
   *
   * @param onProgress callback, {@code (BatchRead, TotalRead) => Unit}.
   * Exceptions will cause process to be interrupted and target file to be deleted.
   */
  def moveFile(from: Path, to: Path, onProgress: (Long, Long) => Unit): Unit = {
    require(Files.isRegularFile(from), "Source file does not exist")
    require(!Files.exists(to), "Target file already exists")
    val fs1 = Files.getFileStore(from)
    val fs2 = Files.getFileStore(to.getParent)
    if (fs1 == fs2) {
      // No need to do deep copy, we can move file instantaneously
      Files.move(from, to)
      val size = Files.size(to)
      onProgress(size, size)
    } else {
      copyFile(from, to, onProgress)
      Files.delete(from)
    }
  }

  /**
   * Copy a {@code from} file to {@code to} file, which shouldn't exist yet.
   *
   * @param onProgress callback, {@code (BatchRead, TotalRead) => Unit}.
   * Exceptions will cause process to be interrupted and target file to be deleted.
   */
  def copyFile(from: Path, to: Path, onProgress: (Long, Long) => Unit): Unit = {
    // Implemented using NIO exclusively to allow in-memory testing
    require(Files.isRegularFile(from), "Source file does not exist")
    require(!Files.exists(to), "Target file already exists")
    val fromAttrs = Files.readAttributes(from, classOf[BasicFileAttributes])
    tryCatchWith(Files.newInputStream(from)) { in =>
      if (fromAttrs.size > 0) {
        tryWith(Files.newByteChannel(to, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) { ch =>
          ch.position(fromAttrs.size - 1)
          ch.write(ByteBuffer.allocate(1))
          assert(ch.size == fromAttrs.size)
        }
      }
      assert(Files.size(to) == fromAttrs.size)
      tryWith(Files.newOutputStream(to)) { out =>
        val buf = Array.fill[Byte](1024 * 1024)(0x00)
        var len = in.read(buf)
        var totalRead: Long = 0
        while (len > 0) {
          totalRead += len
          out.write(buf, 0, len)
          onProgress(len, totalRead)
          len = in.read(buf)
        }
      }
    } {
      case th: Throwable =>
        Files.deleteIfExists(to)
        throw th
    }
  }

  /** Check if filename contains illegal characters */
  def isValidFilename(fn: String): Boolean = {
    // Inefficient, but meh
    fn == asValidFilename(fn)
  }

  /** Make a filename valid by replacing all illegal characters with {@code _} */
  def asValidFilename(fn: String): String = {
    fn replaceAll ("[\\\\/:*?\"<>|]", "_")
  }

  implicit class RichDataOutputStream(out: DataOutputStream) {
    def writeAndFlush(msg: Array[Byte]): Unit = {
      out.write(msg)
      out.flush()
    }
  }

  implicit class RichDataInputStream(in: DataInputStream) {
    def readAvailable(): (Array[Byte], Int) = {
      val buf = new ByteArrayBuffer(1000)
      val tbuf = Array.ofDim[Byte](1000)
      do {
        val readLen = in.read(tbuf)
        buf.append(tbuf, 0, readLen)
      } while (in.available() > 0)
      (buf.buffer, buf.length)
    }
  }
}

object IoUtils extends IoUtils
