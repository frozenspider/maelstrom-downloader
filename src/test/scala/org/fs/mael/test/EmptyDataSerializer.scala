package org.fs.mael.test

import org.fs.mael.core.backend.BackendDataSerializer
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.json4s.JField
import org.json4s.JObject

class EmptyDataSerializer[T <: BackendSpecificEntryData](empty: T) extends BackendDataSerializer[T] {
  protected def serializeFields(bsed: T): Seq[JField] = Seq.empty

  def deserialize(jObj: JObject): T = empty
}
