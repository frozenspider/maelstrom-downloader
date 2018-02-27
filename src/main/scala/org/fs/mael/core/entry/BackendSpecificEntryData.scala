package org.fs.mael.core.entry

trait BackendSpecificEntryData {
  def backendId: String

  def equalsInner(that: BackendSpecificEntryData): Boolean

  def hashCodeInner: Int

  override final def equals(that: Any): Boolean = that match {
    case that: BackendSpecificEntryData => equalsInner(that)
    case _                              => false
  }

  override final def hashCode: Int = hashCodeInner
}
