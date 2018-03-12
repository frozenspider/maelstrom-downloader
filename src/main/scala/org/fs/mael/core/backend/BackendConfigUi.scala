package org.fs.mael.core.backend

import org.fs.mael.core.config.InMemoryConfigManager

// TODO: Components should work for both tabs and properties
trait BackendConfigUi {
  def get(): InMemoryConfigManager
}
