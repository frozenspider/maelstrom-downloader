package org.fs.mael.core.config

/**
 * Used by BackendConfigStore to ensure out-of-scope settings are never queried.
 *
 * This is needed to ensure a fail-fast behaviour if some new setting was added partially.
 */
trait SettingsAccessChecker {
  def backendId: String

  def isSettingIdAccessible(settingId: String): Boolean

  def isSettingAccessible(setting: ConfigSetting[_]): Boolean =
    isSettingIdAccessible(setting.id)
}
