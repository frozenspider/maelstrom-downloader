package org.fs.mael.ui.utils

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

import org.eclipse.jface.preference._
import org.eclipse.swt.SWT
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.events.TypedEvent
import org.eclipse.swt.graphics.FontData
import org.eclipse.swt.graphics.Rectangle
import org.eclipse.swt.widgets._
import org.fs.mael.core.UserFriendlyException
import org.fs.mael.ui.utils.Hotkey._
import org.slf4s.Logger

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

  def createMenuItem(menu: Menu, text: String, parent: Control, hOption: Option[Hotkey])(action: Event => Unit): MenuItem = {
    val mi = new MenuItem(menu, SWT.NONE)
    mi.addListener(SWT.Selection, e => action(e))
    mi.setText(text)
    hOption foreach { h =>
      mi.setText(mi.getText + "\t" + h)
      mi.setAccelerator(h.modOption map (_.code | h.key.accelCode) getOrElse (h.key.accelCode))

      installHotkey(parent, h)(action)
    }
    mi
  }

  def installHotkey(c: Control, h: Hotkey)(action: Event => Unit): Unit = {
    c.addKeyListener(keyPressed {
      case e if h.isApplied(e) =>
        action(toEvent(e))
        e.doit = false
    })
  }

  def getArea(r: Rectangle): Int = r.height * r.width

  def tryShowingError(shell: Shell, log: Logger)(code: => Unit): Unit = {
    try {
      code
    } catch {
      case ex: InterruptedException =>
      // Cancelled by user, do nothing
      case ex: UserFriendlyException =>
        showError(shell, message = ex.getMessage)
      case ex: Throwable =>
        log.error("Unexpected error", ex)
        showError(shell, message = ex.toString)
    }
  }

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

  /** Disable an editor so that its value can't be changed, but the text can be selected (if applicable) */
  def disable(editor: FieldEditor, parent: Composite): Unit = editor match {
    case editor: StringFieldEditor =>
      editor.setEnabled(false, parent)
      editor.getLabelControl(parent).setEnabled(true)
      editor.getTextControl(parent).setEnabled(true)
      editor.getTextControl(parent).setEditable(false)
    case _ =>
      editor.setEnabled(false, parent)
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

  /** Execute code in UI thread iff UI is not disposed yet */
  def syncExecSafely(widget: Widget)(code: => Unit): Unit =
    if (!widget.isDisposed) widget.getDisplay.syncExec { () =>
      if (!widget.isDisposed) code
    }

  def clipboard = Toolkit.getDefaultToolkit.getSystemClipboard

  def getStringFromClipboard(): String =
    clipboard.getData(DataFlavor.stringFlavor).asInstanceOf[String].trim

  /** Shitty SWT design makes this necessary */
  def toEvent(te: TypedEvent): Event = {
    val e = new Event
    e.display = te.display
    e.widget = te.widget
    e.time = te.time
    e.data = te.data
    e
  }

  def partialListener(pf: PartialFunction[Event, Unit]): Listener =
    e => pf applyOrElse (e, NoopAny2UnitPF)

  val NoopAny2UnitPF: PartialFunction[Any, Unit] = { case _ => }
}
