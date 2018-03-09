package org.fs.mael.backend.http

import org.fs.mael.core.backend.BackendDataSerializer
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.Serialization

class HttpDataSerializer extends BackendDataSerializer[HttpEntryData] {
  implicit private val formats = Serialization.formats(NoTypeHints)

  // FIXME
  override def serializeFields(ed: HttpEntryData): Seq[JField] = {
    val jObj: JObject = (
      ("userAgentOption" -> ed.userAgentOption)
    )
    jObj.obj
  }

  override def deserialize(jObj: JObject): HttpEntryData = {
    val ed = new HttpEntryData
    ed.userAgentOption = (jObj \ "userAgentOption").extractOpt[String]
    ed
  }

}
