package org.fs.mael.core.utils

import scala.annotation.tailrec
import org.fs.utility.StopWatch

trait CoreUtils {

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

  implicit class RichAnyRef[T <: AnyRef](ref: T) {
    /** Execute arbitrary code block and return the value itself */
    def withCode(code: T => Unit): T = {
      code(ref)
      ref
    }
  }
}

object CoreUtils extends CoreUtils
