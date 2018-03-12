package org.fs.mael.core.migration

import java.io.File
import java.nio.file.Files

import scala.io.Codec
import scala.io.Source

import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.ConfigSetting
import org.slf4s.Logging

class MigrationManager(globalCfgMgr: ConfigManager, downloadListFile: File) extends Logging {
  import MigrationManager._

  def apply(): Unit = {
    val currVer = globalCfgMgr(VersionSetting)
    val newerVersions = remainingVersions(currVer)
    if (!newerVersions.isEmpty) {
      log.info(s"Applying migration for ${newerVersions.size} version(s)")
      newerVersions.foreach { ver =>
        apply(ver)
      }
      globalCfgMgr.set(VersionSetting, Version.latest)
    }
  }

  def remainingVersions(currVer: Version): Seq[Version] = {
    if (currVer != Version.latest) {
      Version.values.dropWhile(_ != currVer).drop(1)
    } else {
      Seq.empty
    }
  }

  def apply(ver: Version): Unit = {
    ver match {
      case Version.Undefined => // Initial,NOOP
      case Version.v1 =>
        val downloadFileString = Source.fromFile(downloadListFile)(Codec.UTF8).mkString
        val downloadFileString2 = downloadFileString.replace("\"http-https\",", "\"http\",")
        Files.write(downloadListFile.toPath, downloadFileString2.getBytes(Codec.UTF8.charSet))
    }
  }
}

object MigrationManager {

  val VersionSetting: ConfigSetting[Version] =
    ConfigSetting("main.version", Version.Undefined, Version.values)

  sealed abstract class Version(v: Int) extends ConfigSetting.RadioValue(v.toString, v.toString)
  object Version {
    object Undefined extends Version(0)
    object v1 extends Version(1)

    val values = IndexedSeq(Undefined, v1)
    val latest = values.last
  }
}
