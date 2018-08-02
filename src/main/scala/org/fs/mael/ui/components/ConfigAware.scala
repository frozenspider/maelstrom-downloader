package org.fs.mael.ui.components

import org.fs.mael.core.config.IConfigStore

trait ConfigAware {
  protected var cfgOption: Option[IConfigStore] = None

  def cfg = cfgOption.get

  def cfg_=(cfg: IConfigStore): Unit = {
    this.cfgOption = Some(cfg)
  }
}
