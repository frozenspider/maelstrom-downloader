package org.fs.mael.ui.helper

import org.eclipse.swt.SWT
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.graphics.Rectangle
import org.eclipse.swt.widgets._

object SwtHelper {
  def getCurrentMonitor(c: Control): Monitor = {
    val rect = c.getBounds
    val monitors = c.getDisplay.getMonitors
    val intersections = monitors.map(_.getBounds.intersection(rect))
    val maxMonitor = monitors.zip(intersections).maxBy(pair => getArea(pair._2))._1
    maxMonitor
  }

  def centerOnScreen(window: Shell): Unit = {
    val monitor = getCurrentMonitor(window)
    val mBounds = monitor.getBounds
    val wBounds = window.getBounds

    val x = mBounds.x + (mBounds.width - wBounds.width) / 2;
    val y = mBounds.y + (mBounds.height - wBounds.height) / 2;

    window.setLocation(x, y);
  }

  def installDefaultHotkeys(t: Text): Unit = {
    // Ctrl+A
    t.addKeyListener(keyPressed {
      case e if e.stateMask == SWT.CTRL && e.keyCode == 'a' =>
        t.selectAll()
        e.doit = false
    })
  }

  def getArea(r: Rectangle): Int = r.height * r.width

  def showError(shell: Shell, title: String = "Error", message: String): Unit = {
    val popup = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
    popup.setText(title)
    popup.setMessage(message)
    popup.open()
  }

  def keyPressed(pf: PartialFunction[KeyEvent, Unit]) = new KeyPressedListener(pf)

  def keyReleased(pf: PartialFunction[KeyEvent, Unit]) = new KeyReleasedListener(pf)

  //
  // More general stuff
  //

  def partialListener(pf: PartialFunction[Event, Unit]): Listener =
    e => pf applyOrElse (e, NoopAny2UnitPF)

  val NoopAny2UnitPF: PartialFunction[Any, Unit] = { case _ => }

  implicit class RichAnyRef[T <: AnyRef](ref: T) {
    /** Execute arbitrary code block and return the value itself */
    def withChanges(code: T => Unit): T = {
      code(ref)
      ref
    }
  }
}
