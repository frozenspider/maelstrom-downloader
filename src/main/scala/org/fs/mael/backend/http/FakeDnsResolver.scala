package org.fs.mael.backend.http

import org.apache.http.conn.DnsResolver
import java.net.InetAddress

/**
 * Fake resolver for DNS records. Rather than do an actual resolution, it returns a predefined stub
 * which shouldn't be used.
 *
 * This way, we may do an actual DNS resolution later on, possibly delegating it to a proxy.
 *
 * @author FS
 */
object FakeDnsResolver extends DnsResolver {
  val Bytes = Array[Byte](1, 1, 1, 1)
  val Resolution = Array[InetAddress](InetAddress.getByAddress(Bytes))

  override def resolve(host: String): Array[InetAddress] = Resolution
}
