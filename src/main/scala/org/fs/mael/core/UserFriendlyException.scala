package org.fs.mael.core

/**
 * An exception whose message alone is sufficient to let user know what's wrong.
 *
 * @author FS
 */
class UserFriendlyException(message: String)
  extends RuntimeException(message) {

  def this(message: String, cause: Throwable) = {
    this(message)
    initCause(cause)
  }
}
