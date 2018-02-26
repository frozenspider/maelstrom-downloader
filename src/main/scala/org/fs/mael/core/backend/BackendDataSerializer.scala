package org.fs.mael.core.backend

import org.fs.mael.core.entry.BackendSpecificEntryData

trait BackendDataSerializer[BSED <: BackendSpecificEntryData] {
  def serialize(bsed: BSED): String

  def deserialize(bsedString: String): BSED
}
