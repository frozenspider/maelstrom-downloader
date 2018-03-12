package org.fs.mael.test.stub

import java.net.URI

import org.fs.mael.core.config.ConfigSetting

class StubBackend
  extends AbstractSimpleBackend(
    StubBackend.Id
  ) {
  override def isSupported(uri: URI): Boolean = true
}

object StubBackend {
  val Id: String = "dummy"

  val StubSetting = ConfigSetting(Id + ".stubSetting", "defaultValue")
}
