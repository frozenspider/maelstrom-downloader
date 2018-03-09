package org.fs.mael.test.stub

import org.fs.mael.core.backend.BackendDataSerializer
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.json4s.JField
import org.json4s.JObject

class FixedDataSerializer[T <: BackendSpecificEntryData](empty: T) extends BackendDataSerializer[T] {
  protected def serializeFields(bsed: T): Seq[JField] =
    Seq.empty

  def deserialize(jObj: JObject): T =
    empty
}
