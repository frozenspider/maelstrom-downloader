package org.fs.mael.ui.utils

import org.eclipse.swt.events.KeyAdapter
import org.eclipse.swt.events.KeyEvent

class KeyReleasedListener(pf: PartialFunction[KeyEvent, Unit]) extends KeyAdapter {
  private val f = pf orElse SwtUtils.NoopAny2UnitPF

  override def keyReleased(e: KeyEvent): Unit = f(e)
}
