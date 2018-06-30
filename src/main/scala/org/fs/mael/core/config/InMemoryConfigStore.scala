package org.fs.mael.core.config

import java.io.ByteArrayInputStream

import scala.io.Codec

import org.eclipse.jface.preference.PreferenceStore
import org.fs.utility.Imports._
import org.slf4s.Logger

class InMemoryConfigStore extends ConfigStore {
  override val inner = new PreferenceStore()

  /** Reset state to mirror the given one */
  def resetTo(that: ConfigStore): Unit = {
    val diff = computeDiff(that, "")
    resetStoreWithoutEvents(that)
    (diff).foreach {
      case (k, oldV, newV) => inner.firePropertyChangeEvent(k, oldV, newV)
    }
  }

  /** Reset to the state of given config, taking only entries starting with {@code pathPrefix} */
  def resetTo(that: ConfigStore, pathPrefix: String): Unit = {
    val diff = computeDiff(that, pathPrefix + ".")
    resetStoreWithoutEvents(that)
    this.settings = that.settings.filter(_.id.startsWith(pathPrefix + "."))

    // Remove excessive keys
    val keys = that.inner.preferenceNames()
    listerensEnabled = false
    keys filter (!_.startsWith(pathPrefix + ".")) foreach (this.inner.setToDefault)
    listerensEnabled = true

    (diff).foreach {
      case (k, oldV, newV) => inner.firePropertyChangeEvent(k, oldV, newV)
    }
  }

  private def resetStoreWithoutEvents(that: ConfigStore): Unit = {
    // Clear existing properties before loading
    this.inner.preferenceNames foreach (this.inner.setToDefault)
    val bais = new ByteArrayInputStream(that.toByteArray)
    this.inner.load(bais)
    this.settings = that.settings
  }

  private def computeDiff(that: ConfigStore, prefix: String): Seq[(String, Any, Any)] = {
    // Type capture helpers
    def get[T](setting: ConfigSetting[T], store: PreferenceStore): Any =
      setting.toRepr(setting.get(store))
    def getDefault[T](setting: ConfigSetting[T]): Any =
      setting.toRepr(setting.default)

    val oldSettings = this.settings
    val newSettings = that.settings filter (_.id startsWith prefix)
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

  override def save(): Unit = { /* NOOP */ }
}

object InMemoryConfigStore {
  def apply(that: ConfigStore): InMemoryConfigStore = {
    val cfg = new InMemoryConfigStore
    cfg.resetTo(that)
    cfg
  }

  def apply(that: ConfigStore, pathPrefix: String): InMemoryConfigStore = {
    val cfg = new InMemoryConfigStore
    cfg.resetTo(that, pathPrefix)
    cfg
  }

  def apply(serialString: String)(implicit log: Logger): InMemoryConfigStore = {
    val cfg = new InMemoryConfigStore
    applyTo(cfg, serialString)
    cfg
  }

  protected[config] def applyTo(cfg: InMemoryConfigStore, serialString: String)(implicit log: Logger): Unit = {
    // Charset is taken from java.util.Properties.store
    val bytes = serialString.getBytes(Codec.ISO8859.charSet)
    cfg.inner.load(new ByteArrayInputStream(bytes))
    val keys = cfg.inner.preferenceNames()
    cfg.settings = keys.toSeq.map { key =>
      val lookup = ConfigSetting.lookup(key)
      if (lookup.isEmpty) log.warn("No config setting for property key " + key)
      lookup
    }.yieldDefined.toSet
  }
}
