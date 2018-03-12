package org.fs.mael.core.migration

import java.io.File
import java.nio.file.Files

import scala.io.Codec
import scala.io.Source

import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.test.TestUtils._
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class MigrationManagerSpec
  extends FunSuite
  with BeforeAndAfter {

  import MigrationManager._

  val cfgMgr = new InMemoryConfigManager
  val file = File.createTempFile("temp", ".tmp")

  val mgr = new MigrationManager(cfgMgr, file)

  after {
    cfgMgr.reset()
    Files.write(file.toPath(), Array.emptyByteArray)
  }

  test("remaining versions") {
    assert(mgr.remainingVersions(Version.Undefined) === Version.values.tail)
    assert(mgr.remainingVersions(Version.latest) === Seq.empty)
  }

  test("apply v1") {
    val dlFileContent = """[
      |  {
      |    "backendId" : "http-https",
      |    "some-other-stuff" : "some-other-value"
      |  },
      |  {
      |    "some-other-stuff" : "http-https",
      |    "url" : "http://http-https/http-https?http-https=http-https"
      |  }
      |]""".stripMargin
    Files.write(file.toPath(), dlFileContent.getBytes(Codec.UTF8.charSet))
    mgr.apply(Version.v1)
    val dlFileContent2 = Source.fromFile(file)(Codec.UTF8).mkString
    assert(dlFileContent2 === """[
      |  {
      |    "backendId" : "http",
      |    "some-other-stuff" : "some-other-value"
      |  },
      |  {
      |    "some-other-stuff" : "http",
      |    "url" : "http://http-https/http-https?http-https=http-https"
      |  }
      |]""".stripMargin)
  }
}
