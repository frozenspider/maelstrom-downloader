package org.fs.mael.core.config

import org.eclipse.jface.preference.PreferenceStore

class InMemoryConfigManager extends ConfigManager {
  override val store = new PreferenceStore()

  def save(): Unit = {
    // NOOP
  }
}
