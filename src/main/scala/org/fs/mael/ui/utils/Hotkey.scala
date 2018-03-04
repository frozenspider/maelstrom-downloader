package org.fs.mael.ui.utils

import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.SWT

case class Hotkey(maskOption: Option[Int], key: Key) {
  def isApplied(e: KeyEvent): Boolean = {
    e.keyCode == key.keyCode && maskOption.map(_ == e.stateMask).getOrElse((e.stateMask & SWT.MODIFIER_MASK) == 0)
  }
}

object Hotkey {
  def apply(mask: Int, key: Key): Hotkey =
    Hotkey(Some(mask), key)
}

sealed trait Key {
  def keyCode: Int
  def accelCode: Int
}

case class CharKey(c: Char) extends Key {
  override def keyCode: Int = c.toLower
  override def accelCode: Int = c.toUpper
}

case class CtrlKey(code: Int) extends Key {
  override def keyCode: Int = code
  override def accelCode: Int = code
}
