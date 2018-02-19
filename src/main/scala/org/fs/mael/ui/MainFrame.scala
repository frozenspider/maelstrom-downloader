package org.fs.mael.ui

import org.eclipse.swt._
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.events._
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.event.UiSubscriber
import org.fs.mael.ui.helper.SwtHelper._
import java.util.UUID
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.Status
import org.fs.mael.core.event.EventManager

class MainFrame(shell: Shell) extends UiSubscriber {
  private val display = shell.getDisplay
  private var table: Table = _
  private val mainColumnHeaders = Seq(ColumnDef("File Name"), ColumnDef("Downloaded"), ColumnDef("Comment"))
  private val logColumnHeaders = Seq(ColumnDef("", 25), ColumnDef("Date", 80), ColumnDef("Time", 80), ColumnDef("Information", 500))

  def init(): Unit = {
    shell.addDisposeListener(e => onDisposed())
    EventManager.subscribe(this)

    // Layout

    shell.setLayout(new FillLayout(SWT.VERTICAL).withChanges { layout =>
      layout.spacing = 0
    })

    createMenu(shell)

    val group = new Composite(shell, SWT.NONE)
    group.setLayout(new GridLayout().withChanges { layout =>
      layout.horizontalSpacing = 0
      layout.verticalSpacing = 0
      layout.marginWidth = 0
      layout.marginHeight = 0
    })

    createToolbar(group)

    val sashForm = new SashForm(group, SWT.VERTICAL)
    sashForm.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true))

    createTable(sashForm)
    createDetailsPanel(sashForm)

    sashForm.setWeights(Array(10, 10))

    table.setFocus()
  }

  def createMenu(parent: Decorations): Unit = {
    val bar = new Menu(parent, SWT.BAR)
    parent.setMenuBar(bar)

    val fileItem = new MenuItem(bar, SWT.CASCADE)
    fileItem.setText("&File")

    val submenu = new Menu(parent, SWT.DROP_DOWN)
    fileItem.setMenu(submenu)

    val itemExit = new MenuItem(submenu, SWT.PUSH)
    itemExit.addListener(SWT.Selection, e => display.dispose())
    itemExit.setText("Exit")
  }

  def createToolbar(parent: Composite): Unit = {
    val toolbar = new ToolBar(parent, SWT.FLAT)

    val itemAdd = new ToolItem(toolbar, SWT.PUSH)
    itemAdd.setText("Add")
    itemAdd.addListener(SWT.Selection, e => {
      val dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL)
      new AddDownloadFrame(dialog)
      dialog.open()
      println("Add")
    })

    toolbar.pack()
    toolbar.setLayoutData((new GridData()).withChanges { gridData =>
      gridData.horizontalAlignment = GridData.FILL
      gridData.grabExcessHorizontalSpace = true
    })
  }

  private def createTable(parent: Composite): Unit = {
    table = new Table(parent, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION)
    table.setLinesVisible(true)
    table.setHeaderVisible(true)
    table.addKeyListener(keyPressed {
      case e if e.keyCode == SWT.DEL && table.getSelectionCount > 0 =>
        println("Delete")
    })

    mainColumnHeaders.foreach { h =>
      val c = new TableColumn(table, SWT.NONE)
      c.setText(h.name)
      c.setWidth(h.width)
    }

    (1 to 3).map { i =>
      val item = new TableItem(table, SWT.NONE)
      item.setText(0, "x " + i)
      item.setText(1, "y")
      item.setText(2, "this stuff behaves the way I expect")
    }

    table.getColumns.filter(_.getWidth == 0).map(_.pack())
  }

  private def createDetailsPanel(parent: Composite): Unit = {
    // TODO: Show download log
    val logTable = new Table(parent, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION)
    logTable.setLinesVisible(true)
    logTable.setHeaderVisible(true)
    logTable.addKeyListener(keyPressed {
      case e if e.keyCode == SWT.DEL && logTable.getSelectionCount > 0 =>
        println("Delete")
    })

    logColumnHeaders.foreach { h =>
      val c = new TableColumn(logTable, SWT.NONE)
      c.setText(h.name)
      c.setWidth(h.width)
    }

    (1 to 3).map { i =>
      val item = new TableItem(logTable, SWT.NONE)
      item.setText(1, "date " + i)
      item.setText(2, "time " + i)
      item.setText(3, "info " + i)
    }

    logTable.getColumns.filter(_.getWidth == 0).map(_.pack())
  }

  private def onDisposed() {
    println("Disposed")
  }

  //
  // Subscriber trait
  //

  override val subscriberId: String = "swt-ui"

  override def added(de: DownloadEntryView): Unit = display.syncExec { () =>
    val newItem = new TableItem(table, SWT.NONE)
    newItem.setText(0, de.displayName)
    newItem.setText(1, de.downloadedSize.toString)
    newItem.setText(2, de.comment)
  }

  override def removed(de: DownloadEntryView): Unit = display.syncExec { () =>
    val idx = table.getItems.indexWhere(_.getData == de.id)
    if (idx != -1) {
      table.remove(idx)
    }
  }

  override def statusChanged(de: DownloadEntryView, s: Status): Unit = display.syncExec { () =>
    // TODO: NYI
  }

  override def progress(de: DownloadEntryView): Unit = display.syncExec { () =>
    // TODO: NYI
  }

  override def details(de: DownloadEntryView): Unit = display.syncExec { () =>
    // TODO: NYI
  }

  override def logged(de: DownloadEntryView, entry: LogEntry): Unit = display.syncExec { () =>
    // TODO: NYI
  }

  case class ColumnDef(name: String, width: Int = 0)
}
