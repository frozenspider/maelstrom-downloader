package org.fs.mael.core.config

import org.slf4s.Logging

class BackendConfigStore extends InMemoryConfigStore with Logging {
  def this(globalCfg: IGlobalConfigStore, pathPrefix: String) = {
    this()
    resetTo(globalCfg, pathPrefix)
  }

  def this(serialString: String) = {
    this()
    InMemoryConfigStore.applyTo(this, serialString)(log)
  }

  override def resetTo(that: ConfigStore): Unit = {
    super.resetTo(that)
    that match {
      case that: IGlobalConfigStore =>
        ??? // FIXME: Destroy global-linked properties
      case _ => // NOOP
    }
  }

  override def resetTo(that: ConfigStore, pathPrefix: String): Unit = {
    super.resetTo(that, pathPrefix)
    that match {
      case that: IGlobalConfigStore =>
        ??? // FIXME: Destroy global-linked properties
      case _ => // NOOP
    }
  }
}
