package org.fs.mael.backend.http

import org.fs.mael.core.backend.BackendDataSerializer

class HttpDataSerializer extends BackendDataSerializer[HttpEntryData] {
  def serialize(bsed: HttpEntryData): String = {
    ???
  }

  def deserialize(bsedString: String): HttpEntryData = {
    ???
  }
}
