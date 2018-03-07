package org.fs.mael.ui.utils

import org.eclipse.swt.SWT
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.graphics.FontData
import org.eclipse.swt.graphics.Rectangle
import org.eclipse.swt.widgets._
import org.fs.mael.ui.utils.Hotkey._

object SwtUtils {
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
    installHotkey(t, Hotkey(Ctrl, Key('A'))) { e =>
      t.selectAll()
      e.doit = false
    }
  }

  def installDefaultHotkeys(t: Table): Unit = {
    // Ctrl+A
    installHotkey(t, Hotkey(Ctrl, Key('A'))) { e =>
      t.selectAll()
      e.doit = false
    }
  }

  def createMenuItem(menu: Menu, text: String, parent: Control, hOption: Option[Hotkey])(action: => Unit): MenuItem = {
    val mi = new MenuItem(menu, SWT.NONE)
    mi.addListener(SWT.Selection, e => {
      action
      e.doit = false
    })
    mi.setText(text)
    hOption foreach { h =>
      mi.setText(mi.getText + "\t" + h)
      mi.setAccelerator(h.modOption map (_.code | h.key.accelCode) getOrElse (h.key.accelCode))

      installHotkey(parent, h)(e => {
        action
        e.doit = false
      })
    }
    mi
  }

  def installHotkey(c: Control, h: Hotkey)(action: KeyEvent => Unit): Unit = {
    c.addKeyListener(keyPressed {
      case e if h.isApplied(e) => action(e)
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

  def isRowVisible(row: TableItem): Boolean = {
    val table = row.getParent
    val rowBounds = row.getBounds
    val tableBounds = table.getBounds
    val headerHeight = table.getHeaderHeight
    val rowTop = rowBounds.y
    val rowBot = rowTop + rowBounds.height
    val topVisible = rowTop >= headerHeight && rowTop <= tableBounds.height
    val botVisible = rowBot >= headerHeight && rowBot <= tableBounds.height
    topVisible && botVisible
  }

  def scrollTableToBottom(table: Table): Unit = {
    if (table.getItemCount > 0) {
      table.showItem(table.getItem(table.getItemCount - 1))
    }
  }

  val monospacedFontData: FontData = {
    // Taken from https://bugs.eclipse.org/bugs/show_bug.cgi?id=48055
    val osName = System.getProperty("os.name")
    val wsNameLC = SWT.getPlatform.toLowerCase
    val fd = (osName, wsNameLC) match {
      case ("Linux", "gtk")                   => new FontData("Monospace", 10, SWT.NORMAL)
      case ("Linux", _)                       => new FontData("adobe-courier", 12, SWT.NORMAL)
      case (os, _) if os startsWith "Windows" => new FontData("Courier New", 10, SWT.NORMAL)
      case ("Mac OS X", _)                    => new FontData("Monaco", 11, SWT.NORMAL)
      case _                                  => new FontData("Courier New", 10, SWT.NORMAL)
    }
    fd
  }

  //
  // More general stuff
  //

  def partialListener(pf: PartialFunction[Event, Unit]): Listener =
    e => pf applyOrElse (e, NoopAny2UnitPF)

  val NoopAny2UnitPF: PartialFunction[Any, Unit] = { case _ => }
}
