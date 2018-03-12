package org.fs.mael.core.backend

import org.fs.mael.core.config.InMemoryConfigStore

trait BackendConfigUi {
  def get(): InMemoryConfigStore
}
