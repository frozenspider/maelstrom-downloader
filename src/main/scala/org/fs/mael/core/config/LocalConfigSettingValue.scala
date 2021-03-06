package org.fs.mael.core.config

import java.util.UUID

/**
 * Represents a config setting value which can either:
 *
 *  - Default to global config value
 *  - Reference an entry from global config
 *  - Define a new local instance in-place
 *
 * @author FS
 */
sealed trait LocalConfigSettingValue[+T]

object LocalConfigSettingValue {
  trait WithPersistentId {
    def uuid: UUID
    def name: String

    override def equals(that: Any) = that match {
      case that: WithPersistentId => this.uuid == that.uuid
      case _                      => false
    }

    override def hashCode(): Int = this.uuid.hashCode()
  }

  case object Default extends LocalConfigSettingValue[Nothing]

  case class Ref[T <: WithPersistentId](uuid: UUID) extends LocalConfigSettingValue[T]

  case class Embedded[T](value: T) extends LocalConfigSettingValue[T]
}
