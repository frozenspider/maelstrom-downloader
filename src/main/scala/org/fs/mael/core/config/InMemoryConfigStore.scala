package org.fs.mael.core.config

import org.eclipse.jface.preference.PreferenceStore
import org.slf4s.Logging

class InMemoryConfigStore extends ConfigStore with Logging {
  override val inner = new PreferenceStore()

  def save(): Unit = { /* NOOP */ }
}
