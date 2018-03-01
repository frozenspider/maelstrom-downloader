package org.fs.mael.core

class UserFriendlyException(message: String)
  extends RuntimeException(message) {

  def this(message: String, cause: Throwable) = {
    this(message)
    initCause(cause)
  }
}
