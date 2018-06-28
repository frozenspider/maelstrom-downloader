package org.fs.mael.backend.http.utils

import java.net.URLDecoder
import java.net.URLEncoder

import scala.collection.immutable.ListMap
import scala.io.Codec

import org.fs.mael.core.utils.CoreUtils._

object SimpleTableSerializer {
  def serialize(table: Map[String, String]): String = {
    val tableEntryStrings = table.map {
      case (k, v) =>
        HttpUtils.validateCookieCharacterSet(k, v)
        enc(k) + "=" + enc(v)
    }.toSeq
    tableEntryStrings.mkString(";")
  }

  def deserialize(tableSerialString: String): ListMap[String, String] = {
    if (tableSerialString.isEmpty) {
      ListMap.empty
    } else {
      val pairs = tableSerialString.split(";").toIndexedSeq.map(_.split("=")).map { split =>
        requireFriendly(split.size == 2, "Malformed content")
        dec(split(0)) -> dec(split(1))
      }
      ListMap.empty[String, String] ++ pairs
    }
  }

  private def enc(s: String): String = URLEncoder.encode(s, Codec.UTF8.name)

  private def dec(s: String): String = URLDecoder.decode(s, Codec.UTF8.name)
}
