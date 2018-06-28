package org.fs.mael.backend.http

import java.net.URLDecoder
import java.net.URLEncoder

import scala.collection.immutable.ListMap
import scala.io.Codec

import org.fs.mael.core.config.ConfigSetting
import org.fs.mael.core.utils.CoreUtils._

class CookiesConfigSetting(id: String)
  extends ConfigSetting.CustomConfigSetting[Map[String, String], String](id, Map.empty)(ConfigSetting.ImplicitDao.String) {

  def toRepr(cookies: Map[String, String]): String =
    CookiesConfigSetting.serialize(cookies)

  def fromRepr(v: String): Map[String, String] =
    CookiesConfigSetting.deserialize(v)
}

object CookiesConfigSetting {
  def serialize(cookies: Map[String, String]): String = {
    val cookieObjs = cookies.map {
      case (k, v) =>
        HttpUtils.validateCookieCharacterSet(k, v)
        enc(k) + "=" + enc(v)
    }.toSeq
    cookieObjs.mkString(";")
  }

  def deserialize(cookiesSerialString: String): ListMap[String, String] = {
    if (cookiesSerialString.isEmpty) {
      ListMap.empty
    } else {
      val pairs = cookiesSerialString.split(";").toIndexedSeq.map(_.split("=")).map { split =>
        requireFriendly(split.size == 2, "Malformed cookies")
        dec(split(0)) -> dec(split(1))
      }
      ListMap.empty[String, String] ++ pairs
    }
  }

  private def enc(s: String): String = URLEncoder.encode(s, Codec.UTF8.name)

  private def dec(s: String): String = URLDecoder.decode(s, Codec.UTF8.name)
}