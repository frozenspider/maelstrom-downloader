package org.fs.mael.core.utils

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.prop.TableDrivenPropertyChecks

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class IoUtilsSpec
  extends FunSuite
  with TableDrivenPropertyChecks {

  import IoUtils._

  val tmpdir = sys.props("java.io.tmpdir")

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
          copyFile(file1, file2, (_, _) => throw new InterruptedException("He-he!"))
        }
        assert(!Files.exists(file2))

        // Exception, thrown after first batch
        intercept[InterruptedException] {
          var triggeredOnce = false
          copyFile(file1, file2, (_, _) => triggeredOnce match {
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

        copyFile(file1, file2, callback)
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

        moveFile(file1, file2, callback)
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

  test("valid filenames") {
    val strings = Table(
      (("illegal chars")),
      "/", "\\", ":", "*", "?", "\"", "<", ">", "|"
    )
    forAll(strings) { s =>
      val fn = "qwe" + s + ".rty"
      assert(!isValidFilename(s))
      assert(!isValidFilename(fn))
      assert(asValidFilename(fn) === "qwe" + ("_" * s.length) + ".rty")
      assert(isValidFilename(asValidFilename(fn)))
      assert(new File(tmpdir, asValidFilename(fn)).getAbsoluteFile.getName === asValidFilename(fn))
    }
  }
}
