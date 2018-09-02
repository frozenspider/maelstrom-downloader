package org.fs.mael.core.connection

import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.methods.AbstractExecutionAwareRequest
import scala.util.Try

/**
 * Stores all the connection made from specific thread so that they can all be aborted should it be required.
 *
 * Note that this class is NOT reusable, once aborted, it will continue to abort any new connections registered.
 *
 * @author FS
 */
class AbortableConnectionRegistry {
  private var aborted: Boolean = false
  private var reqs: Seq[AbstractExecutionAwareRequest] = Seq.empty

  def register(req: HttpUriRequest): Unit = this.synchronized {
    // Another option would be to somehow wrap (?) the request, intercepting setCancellable...
    req match {
      case req: AbstractExecutionAwareRequest => registerAbortableReq(req)
      case _                                  => throw new AssertionError("Unknown request type!")
    }
  }

  private def registerAbortableReq(req: AbstractExecutionAwareRequest): Unit = this.synchronized {
    if (!aborted) {
      reqs = reqs :+ req
    } else {
      abort(req)
    }
  }

  /** Abort all connections defined in this registry */
  def abort(): Unit = this.synchronized {
    aborted = true
    reqs.foreach(abort)
  }

  private def abort(req: AbstractExecutionAwareRequest): Unit = {
    Try(req.abort())
  }
}
