package org.fs.mael.backend.http

import org.fs.mael.core.entry.BackendSpecificEntryData

class HttpEntryData extends BackendSpecificEntryData {
  override val backendId = HttpBackend.Id

  // Ignored for now
  var userAgentOption: Option[String] = None

  override def equalsInner(that: BackendSpecificEntryData): Boolean = that match {
    case that: HttpEntryData => true
    case _                   => false
  }

  override def hashCodeInner: Int = 31
}
