package org.fs.mael.core

import scala.annotation.tailrec
import org.fs.utility.StopWatch

trait CoreUtils {

  def waitUntil(condition: () => Boolean, timeoutMs: Int): Boolean = {
    val sw = new StopWatch
    @tailrec
    def waitInner(): Boolean = {
      if (condition()) {
        true
      } else if (sw.peek >= timeoutMs) {
        false
      } else {
        Thread.sleep(30)
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
