package org.fs.mael.core.checksum

import java.io.File
import java.net.URI

import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.test.stub.AbstractSimpleBackend
import org.fs.mael.test.stub.StubBackend
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import java.nio.file.Files

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ChecksumsSpec
  extends FunSuite
  with BeforeAndAfter {

  var file: File = _

  before {
    file = File.createTempFile("tmp1", ".tmp")
    Files.write(file.toPath, "My test string".getBytes("UTF8"))
  }

  after {
    file.delete()
  }

  import Checksums._

  test("MD5") {
    val md5 = ChecksumType.MD5
    assert(!isProper(md5, "qwe"))
    assert(!isProper(md5, "123"))
    assert(!isProper(md5, "aa2a28c6443be2ec593d1e04e0dcbc"))
    assert(!isProper(md5, "aa2a28c6443be2ec593d1e04e0dcbcd421"))
    assert(!isProper(md5, "ga2a28c6443be2ec593d1e04e0dcbcd4"))

    assert(isProper(md5, "aa2a28c6443be2ec593d1e04e0dcbcd4"))
    assert(!isProper(md5, "a47a2da1bf0020f5c41c69219179c159b1383194"))
    assert(!isProper(md5, "5358c37942b0126084bb16f7d602788d00416e01bc3fd0132f4458dd355d8e76"))

    assert(check(Checksum(md5, "aa2a28c6443be2ec593d1e04e0dcbcd4"), file))

    assert(guessType("aa2a28c6443be2ec593d1e04e0dcbcd4") === Some(md5))
  }

  test("SHA-1") {
    val sha1 = ChecksumType.SHA1
    assert(!isProper(sha1, "qwe"))
    assert(!isProper(sha1, "123"))
    assert(!isProper(sha1, "a47a2da1bf0020f5c41c69219179c159b13831"))
    assert(!isProper(sha1, "a47a2da1bf0020f5c41c69219179c159b138319411"))
    assert(!isProper(sha1, "g47a2da1bf0020f5c41c69219179c159b1383194"))

    assert(!isProper(sha1, "aa2a28c6443be2ec593d1e04e0dcbcd4"))
    assert(isProper(sha1, "a47a2da1bf0020f5c41c69219179c159b1383194"))
    assert(!isProper(sha1, "5358c37942b0126084bb16f7d602788d00416e01bc3fd0132f4458dd355d8e76"))

    assert(check(Checksum(sha1, "a47a2da1bf0020f5c41c69219179c159b1383194"), file))

    assert(guessType("a47a2da1bf0020f5c41c69219179c159b1383194") === Some(sha1))
  }

  test("SHA-256") {
    val sha256 = ChecksumType.SHA256
    assert(!isProper(sha256, "qwe"))
    assert(!isProper(sha256, "123"))
    assert(!isProper(sha256, "5358c37942b0126084bb16f7d602788d00416e01bc3fd0132f4458dd355d8e"))
    assert(!isProper(sha256, "5358c37942b0126084bb16f7d602788d00416e01bc3fd0132f4458dd355d8e7676"))
    assert(!isProper(sha256, "g358c37942b0126084bb16f7d602788d00416e01bc3fd0132f4458dd355d8e76"))

    assert(!isProper(sha256, "aa2a28c6443be2ec593d1e04e0dcbcd4"))
    assert(!isProper(sha256, "a47a2da1bf0020f5c41c69219179c159b1383194"))
    assert(isProper(sha256, "5358c37942b0126084bb16f7d602788d00416e01bc3fd0132f4458dd355d8e76"))

    assert(check(Checksum(sha256, "5358c37942b0126084bb16f7d602788d00416e01bc3fd0132f4458dd355d8e76"), file))

    assert(guessType("5358c37942b0126084bb16f7d602788d00416e01bc3fd0132f4458dd355d8e76") === Some(sha256))
  }
}
