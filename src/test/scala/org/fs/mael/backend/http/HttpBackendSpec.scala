package org.fs.mael.backend.http

import java.net.URI

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
}
