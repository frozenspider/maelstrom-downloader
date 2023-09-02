package org.fs.mael.core.backend

import java.net.URI

import org.fs.mael.test.stub.AbstractSimpleBackend
import org.fs.mael.test.stub.StubBackend
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[org.scalatestplus.junit.JUnitRunner])
class BackendManagerSpec
  extends AnyFunSuite
  with BeforeAndAfter {

  private var backendMgr: BackendManager = _

  private val backend1 = new SpecificBackend
  private val backend2 = new LessSpecificBackend
  private val backend3 = new StubBackend

  before {
    backendMgr = new BackendManager
  }

  test("add/remove/list/get") {
    backendMgr += (backend1, 1)
    backendMgr += (backend2, -1)
    backendMgr += (backend3, 2)
    assert(backendMgr.list === Seq(backend3, backend1, backend2))
    assert(backendMgr(backend1.id) === backend1)
    assert(backendMgr(backend2.id) === backend2)
    assert(backendMgr(backend3.id) === backend3)
    backendMgr -= backend1
    assert(backendMgr.list === Seq(backend3, backend2))
    assert(backendMgr(backend2.id) === backend2)
    assert(backendMgr(backend3.id) === backend3)
    intercept[IllegalArgumentException] {
      backendMgr(backend1.id)
    }
  }

  test("URL query, backends are queried in the priority order") {
    backendMgr += (backend1, 3)
    backendMgr += (backend2, 2)
    assert(backendMgr.findFor(uri(1)) === Some(backend1))
    assert(backendMgr.findFor(uri(2)) === Some(backend2))
    assert(backendMgr.findFor(uri(3)) === None)
    backendMgr += (backend3, 1)
    assert(backendMgr.findFor(uri(3)) === Some(backend3))
  }

  test("URL query, high-priority backend shades the low-priority one") {
    backendMgr += (backend1, 1)
    backendMgr += (backend2, 2)
    assert(backendMgr.findFor(uri(1)) === Some(backend2))
    assert(backendMgr.findFor(uri(2)) === Some(backend2))
    assert(backendMgr.findFor(uri(3)) === None)
    backendMgr += (backend3, 3)
    assert(backendMgr.findFor(uri(1)) === Some(backend3))
    assert(backendMgr.findFor(uri(2)) === Some(backend3))
    assert(backendMgr.findFor(uri(3)) === Some(backend3))
  }

  private def uri(idx: Int): URI = new URI(s"uriType${idx}://something")

  private class SpecificBackend extends AbstractSimpleBackend(
    "specific"
  ) {
    override def isSupported(uri: URI): Boolean =
      uri.getScheme == "uriType1"
  }

  private class LessSpecificBackend extends AbstractSimpleBackend(
    "less-specific"
  ) {
    override def isSupported(uri: URI): Boolean =
      uri.getScheme == "uriType1" || uri.getScheme == "uriType2"
  }
}
