package org.fs.mael.core.backend

import org.fs.mael.core.entry.BackendSpecificEntryData
import org.json4s._

trait BackendDataSerializer[BSED <: BackendSpecificEntryData] {
  final def serialize(bsed: BSED): JObject = {
    val backendIdField = JField("backendId", JString(bsed.backendId))
    val addedFields = serializeFields(bsed)
    JObject((Seq(backendIdField) ++ addedFields): _*)
  }

  protected def serializeFields(bsed: BSED): Seq[JField]

  def deserialize(jObj: JObject): BSED
}

