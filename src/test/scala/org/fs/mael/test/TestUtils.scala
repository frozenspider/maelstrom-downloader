package org.fs.mael.test

import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.config.ConfigSetting
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
    assert(de1.speedOption === de2.speedOption)
    assert(de1.sections === de2.sections)
    assert(de1.downloadedSize === de2.downloadedSize)
    assert(de1.downloadLog === de2.downloadLog)
    // Have to use plain old equals here
    assert(de1.backendSpecificCfg === de2.backendSpecificCfg)
  }

  implicit class RighCfgMgr(cfgMgr: InMemoryConfigManager) {
    def reset(): InMemoryConfigManager = {
      cfgMgr.resetTo(new InMemoryConfigManager)
      cfgMgr
    }

    def updated[T](setting: ConfigSetting[T], value: T): InMemoryConfigManager = {
      cfgMgr.set(setting, value)
      cfgMgr
    }
  }
}
