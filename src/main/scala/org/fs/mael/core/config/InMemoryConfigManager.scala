package org.fs.mael.core.config

import java.io.ByteArrayInputStream

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
    val bytes = serialString.getBytes("8859_1")
    store.load(new ByteArrayInputStream(bytes))
  }

  /** Reset state to mirror the given one */
  def resetTo(that: ConfigManager): Unit = {
    val bais = new ByteArrayInputStream(that.toByteArray)
    this.store.load(bais)
  }

  /** Reset to the state of given config, taking only entries starting with {@code pathPrefix} */
  def resetTo(that: ConfigManager, pathPrefix: String): Unit = {
    resetTo(that)
    // Remove excessive keys
    val keys = that.store.preferenceNames()
    keys filter (!_.startsWith(pathPrefix + ".")) foreach (this.store.setToDefault)
  }

  def save(): Unit = {
    // NOOP
  }
}
