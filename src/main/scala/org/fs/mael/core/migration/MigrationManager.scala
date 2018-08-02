package org.fs.mael.core.migration

import java.io.File
import java.nio.file.Files

import scala.io.Codec
import scala.io.Source

import org.fs.mael.core.config.ConfigSetting
import org.fs.mael.core.config.IGlobalConfigStore
import org.slf4s.Logging

class MigrationManager(globalCfg: IGlobalConfigStore, downloadListFile: File) extends Logging {
  import MigrationManager._

  // TODO: What if config didn't exist before? No migrations should be applied in that case!

  def apply(): Unit = {
    val currVer = globalCfg(VersionSetting)
    val newerVersions = remainingVersions(currVer)
    if (!newerVersions.isEmpty) {
      log.info(s"Applying migration for ${newerVersions.size} version(s)")
      newerVersions.foreach { ver =>
        apply(ver)
      }
      globalCfg.set(VersionSetting, Version.latest)
    }
  }

  private[migration] def remainingVersions(currVer: Version): Seq[Version] = {
    if (currVer != Version.latest) {
      Version.values.dropWhile(_ != currVer).drop(1)
    } else {
      Seq.empty
    }
  }

  private[migration] def apply(ver: Version): Unit = {
    ver match {
      case Version.Undefined => // Initial,NOOP
      case Version.v1 =>
        updateDownloadListFile(_.replace("\"http-https\",", "\"http\","))
      case Version.v2 =>
        updateDownloadListFile(_.replace("\"backendSpecificCfg\" : \"", "\"backendSpecificCfg\" : \"http|"))
    }
  }

  private[migration] def updateDownloadListFile(change: String => String): Unit = {
    if (downloadListFile.exists) {
      val s1 = Source.fromFile(downloadListFile)(Codec.UTF8).mkString
      val s2 = change(s1)
      Files.write(downloadListFile.toPath, s2.getBytes(Codec.UTF8.charSet))
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
    object v2 extends Version(2)

    val values = IndexedSeq(Undefined, v1, v2)
    val latest = values.last
  }
}
