package org.fs.mael.backend.http

import java.io.File
import java.net.URI

import scala.collection.immutable.ListMap

import org.fs.mael.backend.http.config.HttpSettings
import org.fs.mael.core.config.InMemoryConfigStore
import org.fs.mael.core.transfer.SimpleTransferManager
import org.fs.mael.test.stub.StoringEventManager
import org.junit.runner.RunWith
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HttpBackendSpec
  extends FunSuite {

  val backend = new HttpBackend(new SimpleTransferManager, new InMemoryConfigStore, new StoringEventManager)

  test("supported URLs") {
    assert(backend.isSupported(new URI("http://abcde")))
    assert(backend.isSupported(new URI("https://abcde")))
  }

  test("unsupported URLs - other schemes") {
    assert(!backend.isSupported(new URI("telegram://abcde")))
    assert(!backend.isSupported(new URI("ftp://abcde")))
    assert(!backend.isSupported(new URI("httpss://abcde")))
    assert(!backend.isSupported(new URI("httpp://abcde")))
    assert(!backend.isSupported(new URI("hhttp://abcde")))
  }

  test("unsupported URLs - malformed") {
    assert(!backend.isSupported(new URI("abcde")))
    assert(!backend.isSupported(new URI("http")))
    assert(!backend.isSupported(new URI("https")))
    assert(!backend.isSupported(new URI("http:/abcde")))
    assert(!backend.isSupported(new URI("http:abcde")))
    assert(!backend.isSupported(new URI("http//abcde")))
  }

  test("parse plaintext request") {
    val p = backend.parsePlaintextRequest((_: String), new File(""))

    val parsed1 = p(
      """|GET / HTTP/1.1
         |Host: tas-ix.me
         |Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
         |Upgrade-Insecure-Requests: 1""".stripMargin
    )
    assert(parsed1.uri.toString === "http://tas-ix.me/")
    assert(parsed1.backendSpecificCfg(HttpSettings.UserAgent) === None)
    assert(parsed1.backendSpecificCfg(HttpSettings.Cookies) === ListMap.empty)
    assert(parsed1.backendSpecificCfg(HttpSettings.Headers) === ListMap(
      "Host" -> "tas-ix.me",
      "Accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Upgrade-Insecure-Requests" -> "1"
    ))

    val parsed2 = p(
      """|GET /a/b/c HTTP/1.1
         |Host: tas-ix.me
         |Cookie: a=b; c=d
         |User-Agent: Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:56.0) Gecko/20100101 Firefox/56.0
         |Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
         |Upgrade-Insecure-Requests: 1""".stripMargin
    )
    assert(parsed2.uri.toString === "http://tas-ix.me/a/b/c")
    assert(parsed2.backendSpecificCfg(HttpSettings.UserAgent) === Some(
      "Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:56.0) Gecko/20100101 Firefox/56.0"
    ))
    assert(parsed2.backendSpecificCfg(HttpSettings.Cookies) === ListMap(
      "a" -> "b",
      "c" -> "d"
    ))
    assert(parsed2.backendSpecificCfg(HttpSettings.Headers) === ListMap(
      "Host" -> "tas-ix.me",
      "Accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Upgrade-Insecure-Requests" -> "1"
    ))
  }

  test("parse curl request") {
    val p = backend.parseCurlRequest((_: String), new File(""))

    val parsed1 = p(
      """|curl "https://github.com/frozenspider/maelstrom-downloader/issues"
         | -H "Host: github.com"
         | -H "User-Agent: Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:56.0) Gecko/20100101 Firefox/56.0"
         | -H "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
         | --compressed
         | -H "Cookie: _ga=GA1.2.639706670.1391702045; tz=Asia"%"2FTashkent"
         | -H "DNT: 1"
         | -H "Cache-Control: max-age=0"
         | -H "My-Header: "a""b"\"c\""""".stripMargin.replaceAll("\n", " ")
    )
    assert(parsed1.uri.toString === "https://github.com/frozenspider/maelstrom-downloader/issues")
    assert(parsed1.backendSpecificCfg(HttpSettings.UserAgent) === Some("Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:56.0) Gecko/20100101 Firefox/56.0"))
    assert(parsed1.backendSpecificCfg(HttpSettings.Cookies) === ListMap(
      "_ga" -> "GA1.2.639706670.1391702045",
      "tz" -> "Asia%2FTashkent"
    ))
    assert(parsed1.backendSpecificCfg(HttpSettings.Headers) === ListMap(
      "Host" -> "github.com",
      "Accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "DNT" -> "1",
      "Cache-Control" -> "max-age=0",
      "My-Header" -> "ab\"c\""
    ))

    val parsed2 = p(
      """|curl 'https://github.com/frozenspider/maelstrom-downloader/issues'
         | -H 'Host: github.com'
         | -H 'User-Agent: Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:56.0) Gecko/20100101 Firefox/56.0'
         | -H 'Cookie: _ga=GA1.2.639706670.1391702045; tz=Asia%2FTashkent'
         | -H 'Cache-Control: max-age=0'
         | -H 'My-Header: \'a\''""".stripMargin.replaceAll("\n", " ")
    )
    assert(parsed2.uri.toString === "https://github.com/frozenspider/maelstrom-downloader/issues")
    assert(parsed2.backendSpecificCfg(HttpSettings.UserAgent) === Some("Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:56.0) Gecko/20100101 Firefox/56.0"))
    assert(parsed2.backendSpecificCfg(HttpSettings.Cookies) === ListMap(
      "_ga" -> "GA1.2.639706670.1391702045",
      "tz" -> "Asia%2FTashkent"
    ))
    assert(parsed2.backendSpecificCfg(HttpSettings.Headers) === ListMap(
      "Host" -> "github.com",
      "My-Header" -> "'a'",
      "Cache-Control" -> "max-age=0"
    ))
  }
}
