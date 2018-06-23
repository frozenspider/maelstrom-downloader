package org.fs.mael.backend.http.ui

import java.net.HttpCookie

import scala.collection.immutable.ListMap

import org.fs.mael.core.config.ConfigSetting
import org.fs.mael.core.config.ConfigSetting.CustomConfigSetting
import org.apache.http.cookie.CookieOrigin

class CookiesConfigSetting(id: String) extends CustomConfigSetting[Map[String, String], String](id, Map.empty)(ConfigSetting.ImplicitDao.String) {
  def toRepr(cookies: Map[String, String]): String =
    CookiesConfigSetting.format(cookies)

  def fromRepr(v: String): Map[String, String] =
    CookiesConfigSetting.parse(v)
}

object CookiesConfigSetting {
  import org.apache.http._
  import org.apache.http.message._
  import org.apache.http.cookie._
  import org.apache.http.impl.cookie._

  private val KeyPattern = "[a-zA-Z0-9!#$%&'*.^_`|~+-]+"
  private val ValPattern = "[a-zA-Z0-9!#$%&'()*./:<=>?@\\[\\]^_`{|}~+-]+"

  def format(cookies: Map[String, String]): String = {
    val cookieSpec = new DefaultCookieSpec
    val cookieObjs = cookies.map {
      case (k, v) =>
        require(k matches KeyPattern, s"Key ${k} contains illegal characters")
        require(v matches ValPattern, s"Value ${v} contains illegal characters")
        val c = new HttpCookie(k, v)
        c.setVersion(0)
        c
    }.toSeq
    cookieObjs.mkString(", ")
  }

  def parse(v: String): ListMap[String, String] = {
//    val cookieSpec = new DefaultCookieSpec
//    val header = new BasicHeader("Set-Cookie", v)
//    val parsed2 = cookieSpec.parse(header, new CookieOrigin("localhost", 0, "", false))
//    println(parsed2)

    // We might also use org.apache.http.impl.cookie.DefaultCookieSpec
    import scala.collection.JavaConverters._
    if (v.isEmpty) {
      ListMap.empty
    } else {
      val parsed = HttpCookie.parse(v).asScala.toIndexedSeq
      val pairs = parsed.map(cookieObj => cookieObj.getName -> cookieObj.getValue)
      ListMap.empty[String, String] ++ pairs
    }
  }
}