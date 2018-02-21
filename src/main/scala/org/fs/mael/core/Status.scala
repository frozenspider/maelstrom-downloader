package org.fs.mael.core

/**
 * Download status.
 *
 * @author FS
 */
sealed trait Status {
  val canBeStarted: Boolean
  val canBeStopped: Boolean
  override val toString = this.getClass.getSimpleName.replaceAllLiterally("$", "")
}

object Status {
  object Running extends Status {
    val canBeStarted = false
    val canBeStopped = true
  }
  object Stopped extends Status {
    val canBeStarted = true
    val canBeStopped = false
  }
  object Error extends Status {
    val canBeStarted = true
    val canBeStopped = false
  }
  object Complete extends Status {
    val canBeStarted = false
    val canBeStopped = false
  }
}
