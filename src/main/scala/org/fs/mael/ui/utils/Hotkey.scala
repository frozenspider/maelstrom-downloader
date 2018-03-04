package org.fs.mael.ui.utils

import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.SWT

case class Hotkey(modOption: Option[Hotkey.Modifier], key: Hotkey.Key) {
  def isApplied(e: KeyEvent): Boolean = {
    e.keyCode == key.keyCode && modOption.map(_.code == e.stateMask).getOrElse((e.stateMask & SWT.MODIFIER_MASK) == 0)
  }

  override val toString = {
    modOption.map(_.toString + "+").getOrElse("") + key.toString
  }
}

object Hotkey {
  def apply(key: Key): Hotkey =
    Hotkey(None, key)

  def apply(mod: Modifier, key: Key): Hotkey =
    Hotkey(Some(mod), key)

  sealed trait Key {
    def keyCode: Int
    def accelCode: Int
  }

  object Key {
    def apply(c: Char) = CharKey(c)

    case object Delete extends Key {
      override def keyCode = SWT.DEL
      override def accelCode = SWT.DEL
      override val toString = "Del"
    }

    // TODO: Add more as needed

    case class CharKey(c: Char) extends Key {
      override def keyCode = c.toLower
      override def accelCode = c.toUpper
      override def toString = c.toString
    }
  }

  sealed trait Modifier {
    private[Hotkey] def order: Int
    def code: Int
  }
  case object Ctrl extends Modifier {
    override val order = 1
    override val code = SWT.CTRL
    override val toString = "Ctrl"
  }
  case object Alt extends Modifier {
    override val order = 2
    override val code = SWT.ALT
    override val toString = "Alt"
  }
  case object Shift extends Modifier {
    override val order = 3
    override val code = SWT.SHIFT
    override val toString = "Shift"
  }
  case class Mods(ms: Modifier*) extends Modifier {
    ms sortBy (_.order)
    require(ms.size > 0)
    require(ms.distinct == ms)
    require(!ms.exists(_.isInstanceOf[Mods]))

    override val order = Int.MinValue
    override val code: Int = ms.foldLeft(0)(_ | _.code)
    override val toString = ms.mkString("+")
  }
}
