package org.fs.mael.core.config

import org.eclipse.jface.preference.PreferenceStore
import java.io.ByteArrayOutputStream

trait IConfigStore {
  def settings: Set[ConfigSetting[_]]

  def inner: PreferenceStore

  def save(): Unit

  def initDefault(setting: ConfigSetting[_]): Unit

  def apply[T](setting: ConfigSetting[T]): T

  def resolve[T <: ConfigSettingLocalValue.WithPersistentId](setting: ConfigSetting.RefConfigSetting[T]): T

  def set[T](setting: ConfigSetting[T], value: T): Unit

  def addSettingChangedListener[T](setting: ConfigSetting[T])(f: ConfigChangedEvent[T] => Unit): Unit

  protected def toByteArrayOutputStream: ByteArrayOutputStream = {
    val baos = new ByteArrayOutputStream
    inner.save(baos, null)
    baos
  }

  /**
   * Serializes the store content of this manager into byte array.
   * Note that due to presence of comment with current date/time, its content will differ between invocations!
   */
  def toByteArray: Array[Byte] = {
    toByteArrayOutputStream.toByteArray()
  }
}
