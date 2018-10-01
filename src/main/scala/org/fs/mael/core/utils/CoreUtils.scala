package org.fs.mael.core.utils

import java.io.Closeable

import scala.annotation.tailrec

import org.fs.mael.core.UserFriendlyException
import org.fs.utility.StopWatch

/**
 * The most basic utility methods and implicits to be brought into scope via wildcard import.
 *
 * Should be kept clean and concise to avoid bloating the scope.
 *
 * @author FS
 */
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

  implicit class RichAnyRef[T](ref: T) {
    /** Execute arbitrary code block and return the value itself */
    def withCode(code: T => Unit): T = {
      code(ref)
      ref
    }
  }
}

object CoreUtils extends CoreUtils
