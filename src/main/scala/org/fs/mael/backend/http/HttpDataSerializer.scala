package org.fs.mael.backend.http

import org.fs.mael.core.backend.BackendDataSerializer
import org.json4s._

class HttpDataSerializer extends BackendDataSerializer[HttpEntryData] {
  // FIXME
  override def serializeFields(bsed: HttpEntryData): Seq[JField] = {
    Seq.empty
  }

  override def deserialize(jObj: JObject): HttpEntryData = {
    new HttpEntryData
  }
}
