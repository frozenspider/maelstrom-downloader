package org.fs.mael.core.backend

import org.fs.mael.core.entry.BackendSpecificEntryData

// TODO: Components should work for both tabs and properties
trait BackendConfigUi[BSED <: BackendSpecificEntryData] {
  def get(): BSED
}
