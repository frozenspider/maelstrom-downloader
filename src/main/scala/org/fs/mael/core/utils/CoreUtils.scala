package org.fs.mael.core.utils

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

import scala.annotation.tailrec

import org.apache.commons.lang3.reflect.FieldUtils
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

  /** Analog of Java's try-with-resource */
  def tryCatchWith[C <: Closeable, A](cl: C)(code: C => A)(onThrow: PartialFunction[Throwable, A]): A = {
    try {
      code(cl)
    } catch (
      onThrow
    ) finally {
      cl.close()
    }
  }

  /** Get a nested field (with any access modifier) by path using reflection */
  @throws[NoSuchFieldException]
  def getNestedField(obj: AnyRef, path: List[String]): AnyRef = path match {
    case Nil =>
      obj
    case fieldName :: path =>
      val field = FieldUtils.getField(obj.getClass, fieldName, true)
      val innerObj = field.get(obj)
      getNestedField(innerObj, path)
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

  implicit class RichAnyRef[T](ref: T) {
    /** Execute arbitrary code block and return the value itself */
    def withCode(code: T => Unit): T = {
      code(ref)
      ref
    }
  }
}

object CoreUtils extends CoreUtils
