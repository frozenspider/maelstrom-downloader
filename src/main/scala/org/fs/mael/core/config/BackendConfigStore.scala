package org.fs.mael.core.config

import org.slf4s.Logging
import org.eclipse.jface.preference.PreferenceStore

class BackendConfigStore(
  val innerStore: InMemoryConfigStore,
  accessChecker: SettingsAccessChecker
) extends IConfigStore with Logging {

  protected var allowedSettingsOption: Option[Seq[ConfigSetting[_]]] = None

  override def settings: Set[ConfigSetting[_]] = innerStore.settings

  override def inner: PreferenceStore = innerStore.inner

  override def save(): Unit = innerStore.save()

  override def initDefault(setting: ConfigSetting[_]): Unit = {
    ensureSettingAccess(setting)
    innerStore.initDefault(setting)
  }

  override def apply[T](setting: ConfigSetting[T]): T = {
    ensureSettingAccess(setting)
    innerStore(setting)
  }

  override def set[T](setting: ConfigSetting[T], value: T): Unit = {
    ensureSettingAccess(setting)
    innerStore.set(setting, value)
  }

  override def addSettingChangedListener[T](setting: ConfigSetting[T])(f: ConfigChangedEvent[T] => Unit): Unit = {
    ensureSettingAccess(setting)
    innerStore.addSettingChangedListener(setting)(f)
  }

  protected def ensureSettingAccess(setting: ConfigSetting[_]): Unit = {
    require(accessChecker.isSettingAccessible(setting), s"Setting $setting is outside the scope!")
  }
}
