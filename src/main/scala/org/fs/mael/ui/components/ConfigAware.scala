package org.fs.mael.ui.components

import org.fs.mael.core.config.IConfigStore

trait ConfigAware[C <: IConfigStore] {
  protected var cfgOption: Option[C] = None

  def cfg = cfgOption.get

  def cfg_=(cfg: C): Unit = {
    this.cfgOption = Some(cfg)
  }
}
