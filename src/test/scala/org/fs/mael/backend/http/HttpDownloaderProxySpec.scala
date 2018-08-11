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
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite
import java.util.Arrays
import org.fs.mael.test.proxy.Socks5MockProxy
import org.fs.mael.backend.http.config.HttpSettings
import org.fs.mael.core.config.LocalConfigSettingValue
import org.fs.mael.core.proxy.Proxy
import java.util.UUID
import java.net.InetAddress

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HttpDownloaderProxySpec
  extends FunSuite
  with HttpDownloaderSpecBase
  with BeforeAndAfter
  with BeforeAndAfterAll {

  startServer = false

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

  var socks5Proxy: Socks5MockProxy = _

  override def beforeAll() {
    super[HttpDownloaderSpecBase].beforeAll()
  }

  override def afterAll() {
    super[HttpDownloaderSpecBase].afterAll()
  }

  before {
    super.beforeMethod()
  }

  after {
    super.afterMethod()
    Option(socks5Proxy).map(_.stop())
  }

  test("SOCKS5 - no auth, no DNS") {
    socks5Proxy = new Socks5MockProxy(proxyPort, false, req => proxyResponse, th => failureOption = Some(th))
    socks5Proxy.start()

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
    assert(socks5Proxy.connLog.size === 1)
    assert(socks5Proxy.connLog(0)._1 === Proxy.SOCKS5.AuthMethod.NoAuth)
    assert(socks5Proxy.connLog(0)._2 === Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr(InetAddress.getLoopbackAddress), de.uri.getPort))
    assert(socks5Proxy.connLog(0)._2 !== Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr.Domain("localhost"), de.uri.getPort))
    assert(socks5Proxy.connLog(0)._3.startsWith("GET /mySubUrl/qwe?a=b&c=d HTTP/1.1"))
    assert(socks5Proxy.connLog(0)._3 contains (s"Host: localhost:$uriPort"))
  }

  test("SOCKS5 - user/pass auth, use DNS") {
    socks5Proxy = new Socks5MockProxy(proxyPort, true, req => proxyResponse, th => failureOption = Some(th))
    socks5Proxy.start()

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
    assert(socks5Proxy.connLog.size === 1)
    assert(socks5Proxy.connLog(0)._1 === Proxy.SOCKS5.AuthMethod.UserPass("uname", "123123123"))
    assert(socks5Proxy.connLog(0)._2 !== Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr(InetAddress.getLoopbackAddress), de.uri.getPort))
    assert(socks5Proxy.connLog(0)._2 === Proxy.SOCKS5.Message(0x01, Proxy.SOCKS5.Addr.Domain("localhost"), de.uri.getPort))
    assert(socks5Proxy.connLog(0)._3.startsWith("GET /mySubUrl/qwe?a=b&c=d HTTP/1.1"))
    assert(socks5Proxy.connLog(0)._3 contains (s"Host: localhost:$uriPort"))
  }

  test("SOCKS5 - failure - proxy requires username/password, none provided") {
    var throwable: Throwable = null
    socks5Proxy = new Socks5MockProxy(
      proxyPort,
      true,
      req => {
        failureOption = Some(new IllegalStateException("This shouldn't be reached!"))
        Array.emptyByteArray
      },
      th => throwable = th
    )
    socks5Proxy.start()

    val de = createDownloadEntry()
    de.backendSpecificCfg.set(HttpSettings.ConnectionProxy, LocalConfigSettingValue.Embedded(
      socks5(None, false)
    ))

    expectStatusChangeEvents(de, Status.Running, Status.Error)
    downloader.start(de, 999999)
    await.firedAndStopped()

    assert(throwable !== null)
    assert(transferMgr.bytesRead === 0)
    assert(socks5Proxy.connLog.size === 0)
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
