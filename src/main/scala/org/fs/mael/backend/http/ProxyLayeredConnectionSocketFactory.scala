package org.fs.mael.backend.http

import java.net.InetSocketAddress
import java.net.Socket

import org.apache.http.HttpHost
import org.apache.http.conn.socket.LayeredConnectionSocketFactory
import org.apache.http.protocol.HttpContext
import org.fs.mael.core.connection.AbortableConnectionRegistry
import org.fs.mael.core.proxy.Proxy

/**
 * A variant of `ProxyConnectionSocketFactory` for `LayeredConnectionSocketFactory`.
 *
 * @author FS
 */
class ProxyLayeredConnectionSocketFactory(
  proxy:     Proxy,
  logUpdate: String => Unit,
  wrapped:   LayeredConnectionSocketFactory,
  connReg:   AbortableConnectionRegistry
) extends ProxyConnectionSocketFactory(proxy, logUpdate, wrapped, connReg)
  with LayeredConnectionSocketFactory {

  override def connectSocket(
    connTimeoutMs:  Int,
    socketOrNull:   Socket,
    host:           HttpHost,
    fakeRemoteAddr: InetSocketAddress,
    localAddr:      InetSocketAddress,
    context:        HttpContext
  ) = {
    val socket = super.connectSocket(connTimeoutMs, socketOrNull, host, fakeRemoteAddr, localAddr, context)
    if (proxy == Proxy.NoProxy) {
      // Layer-wrapping is already done by delegate factory
      socket
    } else {
      createLayeredSocket(socket, host.getHostName, fakeRemoteAddr.getPort, context)
    }
  }

  override def createLayeredSocket(
    socket:  Socket,
    target:  String,
    port:    Int,
    context: HttpContext
  ): Socket = {
    val res = wrapped.createLayeredSocket(socket, target, port, context)
    connReg.register(res)
    res
  }
}
