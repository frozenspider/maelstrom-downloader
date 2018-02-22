package org.fs.mael.ui

import org.eclipse.swt._
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.events._
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.BuildInfo
import org.fs.mael.core.BackendManager
import org.fs.mael.core.CoreUtils._
import org.fs.mael.core.Status
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.event.UiSubscriber
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

import com.github.nscala_time.time.Imports._

class MainFrame(shell: Shell) extends Logging {
  private val display = shell.getDisplay
  private val mainColumnHeaders = Seq(ColumnDef("File Name"), ColumnDef("Downloaded"), ColumnDef("Size", 80), ColumnDef("Comment"))
  private val logColumnHeaders = Seq(ColumnDef("", 60 /*25*/ ), ColumnDef("Date", 80), ColumnDef("Time", 80), ColumnDef("Information", 500))

  private var mainTable: Table = _
  private var logTable: Table = _

  private var btnStart: ToolItem = _
  private var btnStop: ToolItem = _

  /** When the download progress update was rendered for the last time, used to avoid excessive load */
  private var lastProgressUpdateTS: Long = System.currentTimeMillis

  def init(): Unit = {
    shell.addListener(SWT.Close, e => onWindowClose(e))
    display.addListener(SWT.Close, e => onAppClose(e))

    // Layout

    shell.setLayout(new FillLayout(SWT.VERTICAL).withCode { layout =>
      layout.spacing = 0
    })

    createMenu(shell)

    val group = new Composite(shell, SWT.NONE)
    group.setLayout(new GridLayout().withCode { layout =>
      layout.horizontalSpacing = 0
      layout.verticalSpacing = 0
      layout.marginWidth = 0
      layout.marginHeight = 0
    })

    createToolbar(group)

    val sashForm = new SashForm(group, SWT.VERTICAL)
    sashForm.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true))

    createMainTable(sashForm)
    createDetailsPanel(sashForm)

    sashForm.setWeights(Array(10, 10))

    // Init

    renderDownloads(DownloadListManager.list())

    adjustColumnWidths(mainTable)
    mainTable.setFocus()
    shell.setText(BuildInfo.fullPrettyName)
    shell.setSize(1000, 600)
    centerOnScreen(shell)

    EventManager.subscribe(subscriber)
  }

  def createMenu(parent: Decorations): Unit = {
    val bar = new Menu(parent, SWT.BAR)
    parent.setMenuBar(bar)

    val fileItem = new MenuItem(bar, SWT.CASCADE)
    fileItem.setText("&File")

    val submenu = new Menu(parent, SWT.DROP_DOWN)
    fileItem.setMenu(submenu)

    val itemExit = new MenuItem(submenu, SWT.PUSH)
    itemExit.addListener(SWT.Selection, e => display.close())
    itemExit.setText("Exit")
  }

  def createToolbar(parent: Composite): Unit = {
    val toolbar = new ToolBar(parent, SWT.FLAT)

    val btnAdd = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnAdd =>
      btnAdd.setText("Add")
      btnAdd.addListener(SWT.Selection, e => {
        val dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL)
        new AddDownloadFrame(dialog)
        dialog.open()
      })
    }

    btnStart = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStart =>
      btnStart.setText("Start")
      btnStart.setEnabled(false)
      btnStart.addListener(SWT.Selection, e => {
        getSelectedDownloadEntries map { de =>
          val pair = BackendManager.findFor(de)
          pair.backend.downloader.start(pair.de)
        }
      })
    }

    btnStop = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStop =>
      btnStop.setText("Stop")
      btnStop.setEnabled(false)
      btnStop.addListener(SWT.Selection, e => {
        getSelectedDownloadEntries map { de =>
          val pair = BackendManager.findFor(de)
          pair.backend.downloader.stop(pair.de)
        }
      })
    }

    toolbar.pack()
    toolbar.setLayoutData((new GridData()).withCode { gridData =>
      gridData.horizontalAlignment = GridData.FILL
      gridData.grabExcessHorizontalSpace = true
    })
  }

  private def createMainTable(parent: Composite): Unit = {
    mainTable = new Table(parent, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION)
    mainTable.setLinesVisible(true)
    mainTable.setHeaderVisible(true)
    mainTable.addKeyListener(keyPressed {
      case e if e.keyCode == SWT.DEL && mainTable.getSelectionCount > 0 =>
        println("Delete")
      // TODO: Implement
    })
    mainTable.addListener(SWT.Selection, e => {
      getSelectedDownloadEntryOption map renderDownloadLog getOrElse { logTable.removeAll() }
    })
    mainTable.addListener(SWT.Selection, e => updateButtonsEnabledState())

    mainColumnHeaders.foreach { h =>
      val c = new TableColumn(mainTable, SWT.NONE)
      c.setText(h.name)
      c.setWidth(h.width)
    }
  }

  private def createDetailsPanel(parent: Composite): Unit = {
    logTable = new Table(parent, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION)
    logTable.setLinesVisible(true)
    logTable.setHeaderVisible(true)

    logColumnHeaders.foreach { h =>
      val c = new TableColumn(logTable, SWT.NONE)
      c.setText(h.name)
      c.setWidth(h.width)
    }

    logTable.getColumns.filter(_.getWidth == 0).map(_.pack())
  }

  private def onWindowClose(e: Event) {
    log.info("Window closed")
    display.close()
  }

  private def onAppClose(e: Event) {
  }

  private def adjustColumnWidths(table: Table): Unit = {
    table.getColumns.filter(_.getWidth == 0).map(_.pack())
  }

  private def renderDownloads(entries: Iterable[DownloadEntryView]): Unit = {
    val sorted = entries.toSeq.sortBy(_.dateCreated)
    sorted.foreach { de =>
      val newRow = new TableItem(mainTable, SWT.NONE)
      fillDownloadRow(newRow, de)
    }
  }

  private def fillDownloadRow(row: TableItem, de: DownloadEntryView): Unit = {
    row.setData(de)
    row.setText(0, de.status + " " + de.displayName)
    row.setText(1, de.downloadedSize.toString)
    row.setText(2, de.sizeOption.getOrElse("").toString)
    row.setText(3, de.comment)
  }

  private def findDownloadRowIdxOption(de: DownloadEntryView): Option[Int] = {
    mainTable.getItems.indexWhere(_.getData match {
      case de2: DownloadEntryView if de2.id == de.id => true
      case _                                         => false
    }) match {
      case -1 => None
      case x  => Some(x)
    }
  }

  /** Return all selected entries */
  private def getSelectedDownloadEntries: Seq[DownloadEntryView] = {
    mainTable.getSelection map (_.getData match {
      case de: DownloadEntryView => de
    })
  }

  /** Return a singular selected entry. If multiple entries are selected, returns {@code None}. */
  private def getSelectedDownloadEntryOption: Option[DownloadEntryView] = {
    if (mainTable.getSelectionCount == 1) {
      getSelectedDownloadEntries.headOption
    } else {
      None
    }
  }

  private def renderDownloadLog(de: DownloadEntryView): Unit = {
    logTable.removeAll()
    de.downloadLog.foreach(appendDownloadLogEntry)
  }

  private def appendDownloadLogEntry(entry: LogEntry): Unit = {
    val lines = entry.details.trim.split("\n")
    def pickColor(tpe: LogEntry.Type): Color = tpe match {
      case LogEntry.Info     => new Color(display, 0xE4, 0xF1, 0xFF)
      case LogEntry.Request  => new Color(display, 0xFF, 0xFF, 0xDD)
      case LogEntry.Response => new Color(display, 0xDD, 0xFF, 0xDD)
      case LogEntry.Error    => new Color(display, 0xFF, 0xDD, 0xDD)
    }
    val wasShowingLastRow =
      if (logTable.getItemCount > 0) {
        val prevLastRow = logTable.getItem(logTable.getItemCount - 1)
        isRowVisible(prevLastRow)
      } else true
    new TableItem(logTable, SWT.NONE).withCode { row =>
      row.setText(0, entry.tpe.toString)
      row.setText(1, entry.date.toString(MainFrame.DateFmt))
      row.setText(2, entry.date.toString(MainFrame.TimeFmt))
      row.setText(3, lines.head.trim)
      row.setBackground(pickColor(entry.tpe))
    }
    lines.tail.foreach { line =>
      new TableItem(logTable, SWT.NONE).withCode { row =>
        row.setText(3, line.trim)
        row.setBackground(pickColor(entry.tpe))
      }
    }
    if (wasShowingLastRow) scrollTableToBottom(logTable)
  }

  private def updateButtonsEnabledState(): Unit = {
    btnStart.setEnabled(getSelectedDownloadEntries exists (_.status.canBeStarted))
    btnStop.setEnabled(getSelectedDownloadEntries exists (_.status.canBeStopped))
  }

  //
  // Subscriber trait
  //

  object subscriber extends UiSubscriber {
    override val subscriberId: String = "swt-ui"

    override def added(de: DownloadEntryView): Unit = syncExecSafely {
      if (!shell.isDisposed) {
        val newRow = new TableItem(mainTable, SWT.NONE)
        fillDownloadRow(newRow, de)
      }
    }

    override def removed(de: DownloadEntryView): Unit = syncExecSafely {
      findDownloadRowIdxOption(de) map (mainTable.remove)
    }

    override def statusChanged(de: DownloadEntryView, prevStatus: Status): Unit = syncExecSafely {
      // Full row update
      findDownloadRowIdxOption(de) map (mainTable.getItem) map (row => fillDownloadRow(row, de))
      updateButtonsEnabledState()
    }

    override def progress(de: DownloadEntryView): Unit = {
      // We assume this is only called by event manager processing thread, so no additional sync needed
      if (System.currentTimeMillis() - lastProgressUpdateTS > MainFrame.ProgressUpdateThresholdMs) {
        syncExecSafely {
          // TODO: Optimize
          findDownloadRowIdxOption(de) map (mainTable.getItem) map (row => fillDownloadRow(row, de))
        }
        lastProgressUpdateTS = System.currentTimeMillis()
      }
    }

    override def detailsChanged(de: DownloadEntryView): Unit = syncExecSafely {
      findDownloadRowIdxOption(de) map (mainTable.getItem) map (row => fillDownloadRow(row, de))
    }

    override def logged(de: DownloadEntryView, entry: LogEntry): Unit = syncExecSafely {
      if (getSelectedDownloadEntryOption == Some(de)) {
        appendDownloadLogEntry(entry)
      }
    }

    /** Execute code in UI thread iff UI is not disposed yet */
    private def syncExecSafely(code: => Unit): Unit =
      display.syncExec { () =>
        if (!shell.isDisposed) code
      }
  }

  //
  // Helper classes
  //

  case class ColumnDef(name: String, width: Int = 0)
}

object MainFrame {
  val ProgressUpdatesPerSecond = 10
  val ProgressUpdateThresholdMs = 100

  val DateFmt = DateTimeFormat.forPattern("yyyy-MM-dd")
  val TimeFmt = DateTimeFormat.forPattern("HH:mm:ss")
}
