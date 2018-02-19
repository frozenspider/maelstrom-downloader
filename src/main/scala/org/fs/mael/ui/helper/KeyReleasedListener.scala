package org.fs.mael.ui.helper

import org.eclipse.swt.events.KeyAdapter
import org.eclipse.swt.events.KeyEvent

class KeyReleasedListener(pf: PartialFunction[KeyEvent, Unit]) extends KeyAdapter {
  private val f = pf orElse SwtHelper.NoopAny2UnitPF

  override def keyReleased(e: KeyEvent): Unit = f(e)
}
