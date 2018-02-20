package org.fs.mael.core

/**
 * Download status.
 *
 * @author FS
 */
sealed trait Status {
  override val toString = this.getClass.getSimpleName.replaceAllLiterally("$", "")
}

object Status {
  object Running extends Status
  object Stopped extends Status
  object Complete extends Status
  object Error extends Status
}
