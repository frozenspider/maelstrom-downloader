package org.fs.mael.core.backend

import org.fs.mael.core.config.InMemoryConfigManager

trait BackendConfigUi {
  def get(): InMemoryConfigManager
}
