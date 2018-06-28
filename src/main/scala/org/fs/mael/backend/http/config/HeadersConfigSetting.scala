package org.fs.mael.backend.http.config

import org.fs.mael.backend.http.utils.HttpUtils
import org.fs.mael.backend.http.utils.SimpleTableSerializer
import org.fs.mael.core.config.ConfigSetting

class HeadersConfigSetting(id: String)
  extends ConfigSetting.CustomConfigSetting[Map[String, String], String](id, Map.empty)(ConfigSetting.ImplicitDao.String) {

  def toRepr(cookies: Map[String, String]): String = {
    HttpUtils.validateHeadersCharacterSet(cookies)
    SimpleTableSerializer.serialize(cookies)
  }

  def fromRepr(v: String): Map[String, String] =
    SimpleTableSerializer.deserialize(v)
}
