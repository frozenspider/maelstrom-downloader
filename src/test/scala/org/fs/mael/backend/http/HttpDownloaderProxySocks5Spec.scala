package org.fs.mael.backend.http

import java.io.File
import java.io.OutputStream
import java.net.SocketException
import java.net.URI
import java.net.URLEncoder

import scala.io.Codec
import scala.util.Random

import org.apache.http._
import org.apache.http.entity._
import org.fs.mael.core.Status
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.checksum.ChecksumType
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import java.util.Arrays
import org.fs.mael.test.proxy.Socks5MockProxy
import org.fs.mael.backend.http.config.HttpSettings
import org.fs.mael.core.config.LocalConfigSettingValue
import org.fs.mael.core.proxy.Proxy
import java.util.UUID
import java.net.InetAddress
import org.fs.mael.test.proxy.Socks5ForwardingProxy
import org.fs.mael.test.proxy.Socks5ForwardingProxy
import java.net.InetSocketAddress

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HttpDownloaderProxySocks5Spec
  extends FunSuite
  with HttpDownloaderSpecBase
  with BeforeAndAfter {

  val proxyResponse = {
    val content = (1 to 5).map(_.toByte).toArray
    val header =
      s"""|HTTP/1.1 200 OK
          |Server: nginx
          |Content-Type: ${ContentType.APPLICATION_OCTET_STREAM}
          |Content-Length: ${content.length}
          |
          |""".stripMargin.getBytes("UTF-8")
    header ++ content
  }
  val proxyPort = 7777

  private var socks5MockProxy: Socks5MockProxy = _
  private var socks5FwProxy: Socks5ForwardingProxy = _

  before {
    super.beforeMethod()
  }

  after {
    super.afterMethod()
    Option(socks5MockProxy).map(_.stop())
    Option(socks5FwProxy).map(_.stop())
  }

  test("no auth, no DNS") {
    socks5MockProxy = new Socks5MockProxy(proxyPort, false, req => proxyResponse, th => failureOption = Some(th))
    socks5MockProxy.start()

    val de = createDownloadEntry()
    de.backendSpecificCfg.set(HttpSettings.ConnectionProxy, LocalConfigSettingValue.Embedded(
      socks5(None, false)
    ))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(readLocalFile(de) === expectedBytes)
    assert(transferMgr.bytesRead === 5)
    assert(socks5MockProxy.connLog.size === 1)
    assert(socks5MockProxy.connLog(0)._1 === Proxy.SOCKS5.AuthMethod.NoAuth)
    assert(socks5MockProxy.connLog(0)._2 === Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr(InetAddress.getLoopbackAddress), de.uri.getPort))
    assert(socks5MockProxy.connLog(0)._2 !== Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr.Domain("localhost"), de.uri.getPort))
    assert(socks5MockProxy.dataReqLog(0).startsWith("GET /mySubUrl/qwe?a=b&c=d HTTP/1.1"))
    assert(socks5MockProxy.dataReqLog(0) contains (s"Host: localhost:$httpPort"))
  }

  test("user/pass auth, use DNS") {
    socks5MockProxy = new Socks5MockProxy(proxyPort, true, req => proxyResponse, th => failureOption = Some(th))
    socks5MockProxy.start()

    val de = createDownloadEntry()
    de.backendSpecificCfg.set(HttpSettings.ConnectionProxy, LocalConfigSettingValue.Embedded(
      socks5(Some(("uname", "123123123")), true)
    ))
    val expectedBytes = Array[Byte](1, 2, 3, 4, 5)

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(readLocalFile(de) === expectedBytes)
    assert(transferMgr.bytesRead === 5)
    assert(socks5MockProxy.connLog.size === 1)
    assert(socks5MockProxy.connLog(0)._1 === Proxy.SOCKS5.AuthMethod.UserPass("uname", "123123123"))
    assert(socks5MockProxy.connLog(0)._2 !== Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr(InetAddress.getLoopbackAddress), de.uri.getPort))
    assert(socks5MockProxy.connLog(0)._2 === Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr.Domain("localhost"), de.uri.getPort))
    assert(socks5MockProxy.dataReqLog(0).startsWith("GET /mySubUrl/qwe?a=b&c=d HTTP/1.1"))
    assert(socks5MockProxy.dataReqLog(0) contains (s"Host: localhost:$httpPort"))
  }

  test("forwarding to HTTP") {
    socks5FwProxy = new Socks5ForwardingProxy(proxyPort, false, new InetSocketAddress("localhost", httpPort), th => failureOption = Some(th))
    socks5FwProxy.start()

    val de = createDownloadEntry()
    de.backendSpecificCfg.set(HttpSettings.ConnectionProxy, LocalConfigSettingValue.Embedded(
      socks5(None, false)
    ))
    val expectedBytes = Array[Byte](5, 4, 3, 2, 1)

    startHttpServer()
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(readLocalFile(de) === expectedBytes)
    assert(transferMgr.bytesRead === 5)
    assert(socks5FwProxy.connLog.size === 1)
    assert(socks5FwProxy.connLog(0)._1 === Proxy.SOCKS5.AuthMethod.NoAuth)
    assert(socks5FwProxy.connLog(0)._2 === Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr(InetAddress.getLoopbackAddress), de.uri.getPort))
  }

  test("forwarding to HTTPS, invalid cert") {
    socks5FwProxy = new Socks5ForwardingProxy(proxyPort, false, new InetSocketAddress("localhost", httpsPort), th => failureOption = Some(th))
    socks5FwProxy.start()

    val de = createDownloadEntry(https = true)
    // We're not disabling HTTPS cert validation and our self-signed cert fails it
    de.backendSpecificCfg.set(HttpSettings.ConnectionProxy, LocalConfigSettingValue.Embedded(
      socks5(None, false)
    ))
    val expectedBytes = Array[Byte](5, 4, 3, 2, 1)

    startHttpsServer()
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Error)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(getLocalFileOption(de) map (f => !f.exists) getOrElse true)
    assert(transferMgr.bytesRead === 0)
    assertLastLogEntry(de, "ssl")
    assert(socks5FwProxy.connLog.size === 1)
    assert(socks5FwProxy.connLog(0)._1 === Proxy.SOCKS5.AuthMethod.NoAuth)
    assert(socks5FwProxy.connLog(0)._2 === Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr(InetAddress.getLoopbackAddress), de.uri.getPort))
  }

  test("forwarding to HTTPS, disabled cert checks") {
    socks5FwProxy = new Socks5ForwardingProxy(proxyPort, false, new InetSocketAddress("localhost", httpsPort), th => failureOption = Some(th))
    socks5FwProxy.start()

    val de = createDownloadEntry(https = true)
    de.backendSpecificCfg.set(HttpSettings.DisableCertificatesValidation, true)
    de.backendSpecificCfg.set(HttpSettings.ConnectionProxy, LocalConfigSettingValue.Embedded(
      socks5(None, false)
    ))
    val expectedBytes = Array[Byte](5, 4, 3, 2, 1)

    startHttpsServer()
    server.respondWith(serveContentNormally(expectedBytes))

    expectStatusChangeEvents(de, Status.Running, Status.Complete)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(readLocalFile(de) === expectedBytes)
    assert(transferMgr.bytesRead === 5)
    assert(socks5FwProxy.connLog.size === 1)
    assert(socks5FwProxy.connLog(0)._1 === Proxy.SOCKS5.AuthMethod.NoAuth)
    assert(socks5FwProxy.connLog(0)._2 === Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr(InetAddress.getLoopbackAddress), de.uri.getPort))
  }

  test("failure - proxy requires username/password, none provided") {
    var throwable: Throwable = null
    socks5MockProxy = new Socks5MockProxy(
      proxyPort,
      true,
      req => {
        failureOption = Some(new IllegalStateException("This shouldn't be reached!"))
        Array.emptyByteArray
      },
      th => throwable = th
    )
    socks5MockProxy.start()

    val de = createDownloadEntry()
    de.backendSpecificCfg.set(HttpSettings.ConnectionProxy, LocalConfigSettingValue.Embedded(
      socks5(None, false)
    ))

    expectStatusChangeEvents(de, Status.Running, Status.Error)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(throwable !== null)
    assert(transferMgr.bytesRead === 0)
    assert(socks5MockProxy.connLog.size === 0)
    assertLastLogEntry(de, "requires auth")
  }

  //
  // Helpers
  //

  def socks5(authOption: Option[(String, String)], dns: Boolean) = Proxy.SOCKS5(
    uuid       = UUID.randomUUID(),
    name       = "",
    host       = "localhost",
    port       = proxyPort,
    authOption = authOption,
    dns        = dns
  )
}
