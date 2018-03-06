package org.fs.mael.core.utils

import java.io.Closeable
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption

import org.fs.utility.StopWatch
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.prop.TableDrivenPropertyChecks

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class CoreUtilsSpec
  extends FunSuite
  with TableDrivenPropertyChecks {

  import CoreUtils._

  test("waitUntil false") {
    assert(waitUntil(100)(false) === false)
  }

  test("waitUntil true") {
    assert(waitUntil(0)(true) === true)
  }

  test("waitUntil condition proc before timeout") {
    val sw = new StopWatch
    assert(waitUntil(100) {
      sw.peek >= 50
    } === true)
  }

  test("waitUntil condition proc after timeout") {
    val sw = new StopWatch
    assert(waitUntil(100) {
      sw.peek >= 150
    } === false)
  }

  test("withCode") {
    var arr = Array.fill(1)(999).withCode { a =>
      assert(a.length === 1)
      assert(a(0) === 999)
      a(0) = -1234
      assert(a(0) === -1234)
    }
    assert(arr(0) === -1234)
  }

  test("tryWith") {
    var isClosed = false
    var codeProcessed = false
    val cl = new Closeable {
      override def close(): Unit = isClosed = true
    }
    assert(!isClosed)
    tryWith(cl)(cl2 => {
      assert(cl2 === cl)
      codeProcessed = true
    })
    assert(codeProcessed)
    assert(isClosed)

    isClosed = false
    codeProcessed = false
    intercept[IllegalArgumentException] {
      tryWith(cl)(cl2 => {
        throw new IllegalArgumentException
        codeProcessed = true
      })
    }
    assert(!codeProcessed)
    assert(isClosed)
  }

  test("copyFile") {
    val tmpdir = sys.props("java.io.tmpdir")
    val fsConfigs = Table(
      (("FS", "from path", "to path")),
      (Jimfs.newFileSystem(Configuration.windows()), "c:\\foo\\file1.bin", "c:\\foo\\file2.bin"),
      (Jimfs.newFileSystem(Configuration.unix()), "/foo/file1.bin", "/foo/file2.bin"),
      (Jimfs.newFileSystem(Configuration.osX()), "/foo/file1.bin", "/foo/file2.bin"),
      (FileSystems.getDefault, s"$tmpdir/foo/file1.bin", s"$tmpdir/foo/file2.bin")
    )
    forAll(fsConfigs) { (fs, fromPath, toPath) =>
      val file1 = fs.getPath(fromPath)
      val file2 = fs.getPath(toPath)

      // 5 MB file content
      val content = ((1 to (5 * 1024 * 1024)) map (_.toByte)).toArray
      try {
        Files.createDirectory(file1.getParent)
        Files.write(file1, content, StandardOpenOption.CREATE_NEW)
        assert(Files.readAllBytes(file1) === content)

        // Exception, thrown immediately
        intercept[InterruptedException] {
          CoreUtils.copyFile(file1, file2, (_, _) => throw new InterruptedException("He-he!"))
        }
        assert(!Files.exists(file2))

        // Exception, thrown after first batch
        intercept[InterruptedException] {
          var triggeredOnce = false
          CoreUtils.copyFile(file1, file2, (_, _) => triggeredOnce match {
            case false => triggeredOnce = true
            case true  => throw new InterruptedException("He-he!")
          })
        }
        assert(!Files.exists(file2))

        // Normal
        var read: Long = 0
        val callback = (part: Long, total: Long) => {
          read += part
        }

        CoreUtils.copyFile(file1, file2, callback)
        assert(read === content.size)
        assert(Files.readAllBytes(file2) === content)
      } finally {
        Files.deleteIfExists(file1)
        Files.deleteIfExists(file2)
        Files.deleteIfExists(file1.getParent)
        Files.deleteIfExists(file2.getParent)
      }
    }
  }

  test("moveFile - same FS, normal execution") {
    val tmpdir = sys.props("java.io.tmpdir")
    val fsConfigs = Table(
      (("FS", "from path", "to path")),
      (Jimfs.newFileSystem(Configuration.windows()), "c:\\foo\\file1.bin", "c:\\foo\\file2.bin"),
      (Jimfs.newFileSystem(Configuration.unix()), "/foo/file1.bin", "/foo/file2.bin"),
      (Jimfs.newFileSystem(Configuration.osX()), "/foo/file1.bin", "/foo/file2.bin"),
      (FileSystems.getDefault, s"$tmpdir/foo/file1.bin", s"$tmpdir/foo/file2.bin")
    )
    forAll(fsConfigs) { (fs, fromPath, toPath) =>
      val file1 = fs.getPath(fromPath)
      val file2 = fs.getPath(toPath)

      // 5 MB file content
      val content = ((1 to (5 * 1024 * 1024)) map (_.toByte)).toArray
      try {
        Files.createDirectory(file1.getParent)
        Files.write(file1, content, StandardOpenOption.CREATE_NEW)
        assert(Files.readAllBytes(file1) === content)

        var timesTriggered = 0
        val callback = (part: Long, total: Long) => {
          timesTriggered += 1
        }

        CoreUtils.moveFile(file1, file2, callback)
        assert(timesTriggered === 1)
        assert(Files.readAllBytes(file2) === content)
        assert(!Files.exists(file1))
      } finally {
        Files.deleteIfExists(file1)
        Files.deleteIfExists(file2)
        Files.deleteIfExists(file1.getParent)
        Files.deleteIfExists(file2.getParent)
      }
    }
  }
}
