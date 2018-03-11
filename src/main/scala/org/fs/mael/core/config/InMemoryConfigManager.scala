package org.fs.mael.core.config

import java.io.ByteArrayInputStream

import scala.io.Codec

import org.eclipse.jface.preference.PreferenceStore

class InMemoryConfigManager extends ConfigManager {
  override val store = new PreferenceStore()

  def this(that: ConfigManager) = {
    this()
    resetTo(that)
  }

  def this(that: ConfigManager, pathPrefix: String) = {
    this()
    resetTo(that, pathPrefix)
  }

  def this(serialString: String) = {
    this()
    // Charset is taken from java.util.Properties.store
    val bytes = serialString.getBytes(Codec.ISO8859.charSet)
    store.load(new ByteArrayInputStream(bytes))
  }

  /** Reset state to mirror the given one */
  def resetTo(that: ConfigManager): Unit = {
    val diff = computeDiff(that, "")
    resetStoreWithoutEvents(that)
    (diff).foreach {
      case (k, oldV, newV) => store.firePropertyChangeEvent(k, oldV, newV)
    }
  }

  /** Reset to the state of given config, taking only entries starting with {@code pathPrefix} */
  def resetTo(that: ConfigManager, pathPrefix: String): Unit = {
    val diff = computeDiff(that, pathPrefix + ".")
    resetStoreWithoutEvents(that)

    // Remove excessive keys
    val keys = that.store.preferenceNames()
    listerensEnabled = false
    keys filter (!_.startsWith(pathPrefix + ".")) foreach (this.store.setToDefault)
    listerensEnabled = true

    (diff).foreach {
      case (k, oldV, newV) => store.firePropertyChangeEvent(k, oldV, newV)
    }
  }

  private def resetStoreWithoutEvents(that: ConfigManager): Unit = {
    val bais = new ByteArrayInputStream(that.toByteArray)
    this.store.load(bais)
  }

  private def computeDiff(that: ConfigManager, prefix: String): Seq[(String, Any, Any)] = {
    val oldSettings = this.settings
    val newSettings = that.settings filter (_.id startsWith prefix)
    val removed: Seq[(String, Any, Any)] = (oldSettings diff newSettings).toSeq map { setting =>
      (setting.id, setting.get(this.store), setting.default)
    }
    val changed: Seq[(String, Any, Any)] = (oldSettings intersect newSettings).toSeq map { setting =>
      (setting.id, setting.get(this.store), setting.get(that.store))
    }
    val added: Seq[(String, Any, Any)] = (newSettings diff oldSettings).toSeq map { setting =>
      (setting.id, setting.default, setting.get(that.store))
    }
    removed ++ changed ++ added
  }

  def save(): Unit = { /* NOOP */ }
}
