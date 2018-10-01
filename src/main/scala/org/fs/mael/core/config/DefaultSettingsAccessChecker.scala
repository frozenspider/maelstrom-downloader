package org.fs.mael.core.config

case class DefaultSettingsAccessChecker(override val backendId: String) extends SettingsAccessChecker {
  override def isSettingIdAccessible(settingId: String): Boolean = {
    settingId startsWith (backendId + ".")
  }
}
