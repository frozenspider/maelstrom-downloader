package org.fs.mael.backend.http

import org.apache.http.conn.DnsResolver
import java.net.InetAddress

/**
 * Fake resolver for DNS records. Rather than do an actual resolution, it returns a predefined stub
 * which shouldn't be used.
 *
 * This way, we may do an actual DNS resolution later on, possibly delegating it to a proxy.
 */
object FakeDnsResolver extends DnsResolver {
  val fakeResolution = Array[InetAddress](InetAddress.getByAddress(Array(1, 1, 1, 1)))

  override def resolve(host: String): Array[InetAddress] = fakeResolution
}
