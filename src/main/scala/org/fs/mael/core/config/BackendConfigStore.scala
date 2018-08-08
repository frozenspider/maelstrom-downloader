package org.fs.mael.core.config

import java.io.ByteArrayInputStream

import scala.io.Codec

import org.eclipse.jface.preference.PreferenceStore
import org.fs.utility.Imports._
import org.slf4s.Logging

/**
 * Serves as a "guarding proxy" for {@code InMemoryConfigStore}
 */
case class BackendConfigStore protected (
  protected val innerCfg: InMemoryConfigStore,
  globalCfg:              IGlobalConfigStore,
  accessChecker:          SettingsAccessChecker
) extends IConfigStore with Logging {

  protected var allowedSettingsOption: Option[Seq[ConfigSetting[_]]] = None

  val backendId = accessChecker.backendId

  override def settings: Set[ConfigSetting[_]] = innerCfg.settings

  override def inner: PreferenceStore = innerCfg.inner

  override def save(): Unit = innerCfg.save()

  override def initDefault(setting: ConfigSetting[_]): Unit = {
    ensureSettingAccess(setting)
    innerCfg.initDefault(setting)
  }

  override def apply[T](setting: ConfigSetting[T]): T = {
    ensureSettingAccess(setting)
    innerCfg(setting)
  }

  override def resolve[T <: LocalConfigSettingValue.WithPersistentId](setting: ConfigSetting.RefConfigSetting[T]): T = {
    ensureSettingAccess(setting)
    innerCfg.resolve(setting)
  }

  def resolve[T <: LocalConfigSettingValue.WithPersistentId](setting: ConfigSetting.LocalEntityConfigSetting[T]): T = {
    ensureSettingAccess(setting)
    val default = globalCfg.resolve(setting.defaultSetting)
    innerCfg(setting) match {
      case LocalConfigSettingValue.Default     => default
      case LocalConfigSettingValue.Ref(uuid)   => globalCfg(setting.refSetting).find(_.uuid == uuid) getOrElse default
      case LocalConfigSettingValue.Embedded(v) => v
    }
  }

  override def set[T](setting: ConfigSetting[T], value: T): Unit = {
    ensureSettingAccess(setting)
    innerCfg.set(setting, value)
  }

  override def addSettingChangedListener[T](setting: ConfigSetting[T])(f: ConfigChangedEvent[T] => Unit): Unit = {
    ensureSettingAccess(setting)
    innerCfg.addSettingChangedListener(setting)(f)
  }

  def toSerialForm: (String, String) = {
    (backendId, innerCfg.toSerialString)
  }

  /** Reset to the state of given config, taking only accessible entries */
  def resetTo(that: IConfigStore): Unit = {
    val diff = computeDiff(that)
    resetStoreWithoutEvents(that)
    this.innerCfg.settings = that.settings.filter(accessChecker.isSettingAccessible)

    // Remove excessive keys
    val keys = that.inner.preferenceNames()
    this.innerCfg.listerensEnabled = false
    keys filterNot (accessChecker.isSettingIdAccessible) foreach (this.innerCfg.inner.setToDefault)
    this.innerCfg.listerensEnabled = true

    (diff).foreach {
      case (k, oldV, newV) => inner.firePropertyChangeEvent(k, oldV, newV)
    }
  }

  private def resetStoreWithoutEvents(that: IConfigStore): Unit = {
    // Clear existing properties before loading
    this.innerCfg.inner.preferenceNames foreach (this.inner.setToDefault)
    val bais = new ByteArrayInputStream(that.toByteArray)
    this.innerCfg.inner.load(bais)
    this.innerCfg.settings = that.settings
  }

  private def computeDiff(that: IConfigStore): Seq[(String, Any, Any)] = {
    // Type capture helpers
    def get[T](setting: ConfigSetting[T], store: PreferenceStore): Any =
      setting.toRepr(setting.get(store))
    def getDefault[T](setting: ConfigSetting[T]): Any =
      setting.toRepr(setting.default)

    val oldSettings = this.settings
    val newSettings = that.settings filter (accessChecker.isSettingAccessible)
    val removed: Seq[(String, Any, Any)] = (oldSettings diff newSettings).toSeq map { setting =>
      (setting.id, get(setting, this.inner), getDefault(setting))
    }
    val changed: Seq[(String, Any, Any)] = (oldSettings intersect newSettings).toSeq map { setting =>
      (setting.id, get(setting, this.inner), get(setting, that.inner))
    }
    val added: Seq[(String, Any, Any)] = (newSettings diff oldSettings).toSeq map { setting =>
      (setting.id, getDefault(setting), get(setting, that.inner))
    }
    removed ++ changed ++ added
  }

  protected def ensureSettingAccess(setting: ConfigSetting[_]): Unit = {
    require(accessChecker.isSettingIdAccessible(setting.id), s"Setting $setting is outside the scope!")
  }
}

object BackendConfigStore extends Logging {
  def apply(
    globalStore:   IGlobalConfigStore,
    accessChecker: SettingsAccessChecker
  ): BackendConfigStore = {
    new BackendConfigStore(new InMemoryConfigStore, globalStore, accessChecker)
  }

  def apply(
    baseStore:     IConfigStore,
    globalStore:   IGlobalConfigStore,
    accessChecker: SettingsAccessChecker
  ): BackendConfigStore = {
    val result = this(globalStore, accessChecker)
    result.resetTo(baseStore)
    result
  }

  def apply(
    serialString:  String,
    globalStore:   IGlobalConfigStore,
    accessChecker: SettingsAccessChecker
  ): BackendConfigStore = {
    val baseStore = new InMemoryConfigStore
    // Charset is taken from java.util.Properties.store
    val bytes = serialString.getBytes(Codec.ISO8859.charSet)
    baseStore.inner.load(new ByteArrayInputStream(bytes))
    val keys = baseStore.inner.preferenceNames()
    baseStore.settings = keys.toSeq.map { key =>
      val lookup = ConfigSetting.lookup(key)
      if (lookup.isEmpty) log.warn("No config setting for property key " + key)
      lookup
    }.yieldDefined.toSet
    this(baseStore, globalStore, accessChecker)
  }
}
