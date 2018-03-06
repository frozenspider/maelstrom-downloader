package org.fs.mael.core.utils

import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.file.Files

import scala.annotation.tailrec

import org.fs.mael.core.UserFriendlyException
import org.fs.utility.StopWatch

trait CoreUtils {
  /** Check a requirement, throw a {@code UserFriendlyException} if it fails */
  def requireFriendly(requirement: Boolean, msg: => String): Unit = {
    if (!requirement) failFriendly(msg)
  }

  /** Throw a {@code UserFriendlyException} */
  def failFriendly(msg: String): Nothing = {
    throw new UserFriendlyException(msg)
  }

  def waitUntil(timeoutMs: Int)(condition: => Boolean): Boolean = {
    val sw = new StopWatch
    @tailrec
    def waitInner(): Boolean = {
      if (condition) {
        true
      } else if (sw.peek >= timeoutMs) {
        false
      } else {
        Thread.sleep(10)
        waitInner()
      }
    }
    waitInner()
  }

  /** Analog of Java's try-with-resource */
  def tryWith[C <: Closeable, A](cl: C)(code: C => A): A = {
    try {
      code(cl)
    } finally {
      cl.close()
    }
  }

  /**
   * Move a {@code from} file to {@code to} file, which shouldn't exist yet.
   *
   * @param onProgress callback, {@code (BatchRead, TotalRead) => Unit}.
   * Exceptions will cause process to be interrupted and target file to be deleted.
   */
  def moveFile(from: File, to: File, onProgress: (Long, Long) => Unit): Unit = {
    require(from.exists, "Source file does not exist")
    require(!to.exists, "Target file already exists")
    val fs1 = Files.getFileStore(from.toPath)
    val fs2 = Files.getFileStore(to.getParentFile.toPath)
    if (fs1 == fs2) {
      // No need to do deep copy, we can move file instantaneously
      val renameResult = from renameTo to
      assert(renameResult)
      onProgress(from.length, from.length)
    } else {
      copyFile(from, to, onProgress)
      from.delete()
    }
  }

  /**
   * Copy a {@code from} file to {@code to} file, which shouldn't exist yet.
   *
   * @param onProgress callback, {@code (BatchRead, TotalRead) => Unit}.
   * Exceptions will cause process to be interrupted and target file to be deleted.
   */
  def copyFile(from: File, to: File, onProgress: (Long, Long) => Unit): Unit = {
    require(from.exists, "Source file does not exist")
    require(!to.exists, "Target file already exists")
    try {
      tryWith(new FileInputStream(from)) { fis =>
        tryWith(try {
          val to2 = new RandomAccessFile(to, "rw")
          to2.setLength(from.length)
          to2
        } catch {
          case ex: Exception => failFriendly(ex.getMessage)
        }) { to2 =>
          val buf = Array.fill[Byte](1024 * 1024)(0x00)
          var len = fis.read(buf)
          var totalRead: Long = 0
          while (len > 0) {
            totalRead += len
            to2.write(buf, 0, len)
            onProgress(len, totalRead)
            len = fis.read(buf)
          }
        }
      }
    } catch {
      case th: Throwable =>
        to.delete()
        throw th
    }
  }

  implicit class RichAnyRef[T <: AnyRef](ref: T) {
    /** Execute arbitrary code block and return the value itself */
    def withCode(code: T => Unit): T = {
      code(ref)
      ref
    }
  }
}

object CoreUtils extends CoreUtils
