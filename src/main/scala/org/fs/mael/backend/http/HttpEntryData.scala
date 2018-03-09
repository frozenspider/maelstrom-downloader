package org.fs.mael.backend.http

import org.apache.commons.lang3.builder.EqualsBuilder
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.fs.mael.core.entry.BackendSpecificEntryData

class HttpEntryData extends BackendSpecificEntryData {
  override val backendId = HttpBackend.Id

  // Ignored for now
  var userAgentOption: Option[String] = None

  override def equalsInner(that: BackendSpecificEntryData): Boolean = that match {
    case that: HttpEntryData =>
      (new EqualsBuilder)
        .append(this.userAgentOption, that.userAgentOption)
        .build()
    case _ => false
  }

  override def hashCodeInner: Int =
    (new HashCodeBuilder)
      .append(this.userAgentOption)
      .toHashCode
}
