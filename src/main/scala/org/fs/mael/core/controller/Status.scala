package org.fs.mael.core.controller

/**
 * Download status.
 *
 * @author FS
 */
sealed trait Status

object Status {
  object Running extends Status
  object Stopped extends Status
  object Complete extends Status
  object Error extends Status
}
