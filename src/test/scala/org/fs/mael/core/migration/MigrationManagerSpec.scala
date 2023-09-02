package org.fs.mael.core.migration

import java.io.File
import java.nio.file.Files

import scala.io.Codec
import scala.io.Source

import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.config.InMemoryConfigStore
import org.fs.mael.test.TestUtils._
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[org.scalatestplus.junit.JUnitRunner])
class MigrationManagerSpec
  extends AnyFunSuite
  with BeforeAndAfter {

  import MigrationManager._

  var cfg = new InMemoryConfigStore with IGlobalConfigStore
  val file = File.createTempFile("temp", ".tmp")

  val mgr = new MigrationManager(cfg, file)

  after {
    cfg = new InMemoryConfigStore with IGlobalConfigStore
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
