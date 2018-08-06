package org.fs.mael.core.config

/** Used to separate GlobalConfigStore users from implementation details */
trait IGlobalConfigStore extends IConfigStoreImpl {
  override def initDefault(setting: ConfigSetting[_]): Unit = {
    super.initDefault(setting)
    setting match {
      case setting: ConfigSetting.LocalEntityConfigSetting[_] =>
        throw new IllegalArgumentException(s"Can't define local setting '${setting.id}' on global config")
      case _ => // NOOP
    }
  }
}
