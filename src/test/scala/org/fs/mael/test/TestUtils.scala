package org.fs.mael.test

import java.util.UUID

import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.ConfigSetting
import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.config.InMemoryConfigStore
import org.fs.mael.core.config.LocalConfigSettingValue
import org.fs.mael.core.config.SettingsAccessChecker
import org.fs.mael.core.entry.DownloadEntry
import org.scalatest.Assertions

object TestUtils extends Assertions {
  def assertDownloadEntriesEqual(de1: DownloadEntry, de2: DownloadEntry): Unit = {
    assert(de1.id === de2.id)
    assert(de1.dateCreated === de2.dateCreated)
    assert(de1.backendId === de2.backendId)
    assert(de1.uri === de2.uri)
    assert(de1.location === de2.location)
    assert(de1.filenameOption === de2.filenameOption)
    assert(de1.checksumOption === de2.checksumOption)
    assert(de1.comment === de2.comment)
    assert(de1.status === de2.status)
    assert(de1.sizeOption === de2.sizeOption)
    assert(de1.supportsResumingOption === de2.supportsResumingOption)
    assert(de1.sections === de2.sections)
    assert(de1.downloadedSize === de2.downloadedSize)
    assert(de1.downloadLog === de2.downloadLog)
    // Have to use plain old equals here
    assert(de1.backendSpecificCfg === de2.backendSpecificCfg)
  }

  def emptyGlobalCfg() = new InMemoryConfigStore with IGlobalConfigStore

  val DummySettingsAccessChecker = new SettingsAccessChecker {
    override val backendId = "<none!>"
    override def isSettingIdAccessible(settingId: String): Boolean = true
  }

  implicit class RichBackendConfigStore(val cfg: BackendConfigStore) {
    def reset(): cfg.type = {
      cfg.resetTo(new InMemoryConfigStore)
      cfg
    }

    def updated[T](setting: ConfigSetting[T], value: T): cfg.type = {
      cfg.set(setting, value)
      cfg
    }
  }

  implicit class RichMemoryConfigStore(val cfg: InMemoryConfigStore) {
    def updated[T](setting: ConfigSetting[T], value: T): cfg.type = {
      cfg.set(setting, value)
      cfg
    }
  }

  object ConfigValueClasses {
    sealed trait ABC extends LocalConfigSettingValue.WithPersistentId {
      override val name = toString()
    }
    case object A extends ABC {
      override val uuid = UUID.randomUUID()
    }
    case class B(uuid: UUID) extends ABC
    case class C(uuid: UUID, v1: String, b2: Int) extends ABC
    val AbcClassses: Seq[Class[_ <: ABC]] = Seq(A.getClass, classOf[B], classOf[C])
  }
}
