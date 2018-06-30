package org.fs.mael.core.backend.ui

import org.fs.mael.core.config.BackendConfigStore

trait BackendConfigUi {
  def get(): BackendConfigStore
}
