package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.test.stub.AbstractSimpleBackend
import org.fs.mael.test.stub.StubBackend
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class BackendManagerSpec
  extends FunSuite
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

  test("Entry query") {
    backendMgr += (backend1, 1)
    backendMgr += (backend2, 2)
    val universalUri = uri(1)
    val de1 = backend1.create(universalUri, new File("1"), None, None, "comment1", None)
    val de2 = backend2.create(universalUri, new File("2"), None, None, "comment2", None)
    val de3 = backend3.create(universalUri, new File("3"), None, None, "comment3", None)

    val pair1 = backendMgr.getCastedPair(de1)
    assert(pair1.backend === backend1)
    assert(pair1.de === de1)
    assert(pair1.de.backendSpecificData.isInstanceOf[SpecificBackendEntryData])

    val pair2 = backendMgr.getCastedPair(de2)
    assert(pair2.backend === backend2)
    assert(pair2.de === de2)
    assert(pair2.de.backendSpecificData.isInstanceOf[LessSpecificBackendEntryData])

    intercept[IllegalArgumentException] {
      backendMgr.getCastedPair(de3)
    }
    backendMgr += (backend3, 3)
    val pair3 = backendMgr.getCastedPair(de3)
    assert(pair3.backend === backend3)
    assert(pair3.de === de3)
    assert(pair3.de.backendSpecificData.isInstanceOf[StubBackend.StubEntryData])
  }

  private def uri(idx: Int): URI = new URI(s"uriType${idx}://something")

  private class SpecificBackend extends AbstractSimpleBackend[SpecificBackendEntryData](
    "specific"
  ) {
    override def isSupported(uri: URI): Boolean =
      uri.getScheme == "uriType1"
    override val defaultData = new SpecificBackendEntryData
  }

  private class LessSpecificBackend extends AbstractSimpleBackend[LessSpecificBackendEntryData](
    "less-specific"
  ) {
    override def isSupported(uri: URI): Boolean =
      uri.getScheme == "uriType1" || uri.getScheme == "uriType2"
    override val defaultData = new LessSpecificBackendEntryData
  }

  class SpecificBackendEntryData extends BackendSpecificEntryData {
    def backendId: String = backend1.id
    def equalsInner(that: BackendSpecificEntryData): Boolean = this eq that
    def hashCodeInner: Int = backendId.hashCode
  }

  class LessSpecificBackendEntryData extends BackendSpecificEntryData {
    def backendId: String = backend2.id
    def equalsInner(that: BackendSpecificEntryData): Boolean = this eq that
    def hashCodeInner: Int = backendId.hashCode
  }
}
