package org.fs.mael.core

trait CoreUtils {
  implicit class RichAnyRef[T <: AnyRef](ref: T) {
    // TODO: Rename to withCode
    /** Execute arbitrary code block and return the value itself */
    def withChanges(code: T => Unit): T = {
      code(ref)
      ref
    }
  }
}

object CoreUtils extends CoreUtils
