package org.fs.mael.core.config

import java.io.ByteArrayOutputStream

import scala.io.Codec

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceStore
import org.eclipse.jface.util.PropertyChangeEvent

trait ConfigStore {
  protected[config] var settings: Set[ConfigSetting[_]] = Set.empty
  protected[config] var listerensEnabled: Boolean = true

  val inner: PreferenceStore

  def save(): Unit

  /**
   * Initialize default values for the given config setting.
   * Invoking this multiple times is safe and does nothing.
   */
  def initDefault(setting: ConfigSetting[_]): Unit = {
    settings += setting
    ConfigStore.initDefault(inner, setting)
  }

  def apply[T](setting: ConfigSetting[T]): T = {
    initDefault(setting)
    setting.get(inner) ensuring (_ != null)
  }

  def set[T](setting: ConfigSetting[T], value: T): Unit = {
    initDefault(setting)
    setting.set(inner, value)
    save()
  }

  def addSettingChangedListener[T](setting: ConfigSetting[T])(f: ConfigChangedEvent[T] => Unit): Unit = {
    inner.addPropertyChangeListener(e => e match {
      case e if e.getProperty == setting.id => notifySettingChanged(setting, e, f)
      case _                                => // NOOP
    })
  }

  protected def notifySettingChanged[T](setting: ConfigSetting[T], e: PropertyChangeEvent, f: ConfigChangedEvent[T] => Unit): Unit = {
    if (listerensEnabled) {
      val e2 = ConfigChangedEvent[T](setting.fromRepr(e.getOldValue.asInstanceOf[setting.Repr]), setting.fromRepr(e.getNewValue.asInstanceOf[setting.Repr]))
      f(e2)
    }
  }

  /**
   * Serializes the store content of this manager into byte array.
   * Note that due to presence of comment with current date/time, its content will differ between invocations!
   */
  protected[config] def toByteArray: Array[Byte] = {
    val baos = new ByteArrayOutputStream
    inner.save(baos, null)
    baos.toByteArray()
  }

  def toSerialString: String = {
    val baos = new ByteArrayOutputStream
    inner.save(baos, null)
    // Charset is taken from java.util.Properties.store
    val lines = baos.toString(Codec.ISO8859.name).split("[\r\n]+").sorted
    // Removing comments
    lines.filter(!_.startsWith("#")).mkString("\n")
  }

  override def toString(): String = {
    val keys = inner.preferenceNames().sorted
    val content = keys map (k => s"k -> ${inner.getString(k)}") mkString ", "
    this.getClass.getSimpleName + "(" + content + ")"
  }

  override def equals(that: Any): Boolean = that match {
    case that: ConfigStore => this.toSerialString == that.toSerialString
    case _                 => false
  }

  override def hashCode: Int = this.toSerialString.hashCode
}

object ConfigStore {
  /**
   * Initialize default values for the given config setting.
   * Invoking this multiple times is safe and does nothing.
   */
  def initDefault(store: IPreferenceStore, setting: ConfigSetting[_]): Unit = {
    setting.setDefault(store)
  }
}
