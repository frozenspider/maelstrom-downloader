package org.fs.mael.ui

import java.util.LinkedHashMap

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.dialogs.MessageDialogWithToggle
import org.eclipse.swt._
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.events._
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.BuildInfo
import org.fs.mael.core.CoreUtils._
import org.fs.mael.core.Status
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.event.UiSubscriber
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

import com.github.nscala_time.time.Imports._

class MainFrame(
  shell:           Shell,
  resources:       Resources,
  cfgMgr:          ConfigManager,
  downloadListMgr: DownloadListManager
) extends Logging {
  private val display = shell.getDisplay
  private val mainColumnDefs = new Columns[DownloadEntryView](
    ColumnDefExt("File Name", de => de.displayName),
    ColumnDefExt("Downloaded", downloadEntityFormat.downloadedSize),
    ColumnDefExt("Size", downloadEntityFormat.size, 80),
    ColumnDefExt("Comment", de => de.comment, 200),
    ColumnDefExt("Added", de => de.dateCreated.toString(MainFrame.DateTimeFmt), 120)
  )
  private val logColumnHeaders = Seq(ColumnDef("", 24), ColumnDef("Date", 80), ColumnDef("Time", 80), ColumnDef("Information", 500))

  private var mainTable: Table = _
  private var logTable: Table = _

  private var btnStart: ToolItem = _
  private var btnStop: ToolItem = _

  /** When the download progress update was rendered for the last time, used to avoid excessive load */
  private var lastProgressUpdateTS: Long = System.currentTimeMillis

  def init(): Unit = {
    shell.addListener(SWT.Close, onWindowClose)

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

    renderDownloads(downloadListMgr.list())

    adjustColumnWidths(mainTable)
    mainTable.setFocus()
    shell.setText(BuildInfo.fullPrettyName)
    shell.setSize(1000, 600)
    centerOnScreen(shell)

    EventManager.subscribe(subscriber)
  }

  private def createMenu(parent: Decorations): Unit = {
    val bar = new Menu(parent, SWT.BAR)
    parent.setMenuBar(bar)

    new MenuItem(bar, SWT.CASCADE).withCode { menuItem =>
      menuItem.setText("&File")

      val submenu = new Menu(parent, SWT.DROP_DOWN)
      menuItem.setMenu(submenu)

      val itemExit = new MenuItem(submenu, SWT.PUSH)
      itemExit.setText("Exit")
      itemExit.addListener(SWT.Selection, tryExit)
    }

    new MenuItem(bar, SWT.CASCADE).withCode { menuItem =>
      menuItem.setText("&Service")

      val submenu = new Menu(parent, SWT.DROP_DOWN)
      menuItem.setMenu(submenu)

      val itemOptions = new MenuItem(submenu, SWT.PUSH)
      itemOptions.setText("Options")
      itemOptions.addListener(SWT.Selection, e => cfgMgr.showDialog(shell))
    }
  }

  private def createToolbar(parent: Composite): Unit = {
    val toolbar = new ToolBar(parent, SWT.FLAT)

    val btnAdd = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnAdd =>
      btnAdd.setText("Add")
      btnAdd.addListener(SWT.Selection, e => {
        val dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL)
        new AddDownloadFrame(dialog, cfgMgr, downloadListMgr)
        dialog.open()
      })
    }

    btnStart = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStart =>
      btnStart.setText("Start")
      btnStart.setEnabled(false)
      btnStart.addListener(SWT.Selection, e => {
        getSelectedDownloadEntries map { de =>
          val pair = BackendManager.getCastedPair(de)
          pair.backend.downloader.start(pair.de, cfgMgr.getProperty(ConfigOptions.NetworkTimeout))
        }
      })
    }

    btnStop = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStop =>
      btnStop.setText("Stop")
      btnStop.setEnabled(false)
      btnStop.addListener(SWT.Selection, e => {
        getSelectedDownloadEntries map { de =>
          val pair = BackendManager.getCastedPair(de)
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

    mainColumnDefs.content.map(_.toColumnDef).foreach { cd =>
      val c = new TableColumn(mainTable, SWT.NONE)
      c.setText(cd.name)
      c.setWidth(cd.width)
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

    // Since standard images in table remove background, we have to draw them manually instead
    logTable.addListener(SWT.PaintItem, e => {
      val row = e.item.asInstanceOf[TableItem]
      row.getData match {
        case entry: LogEntry =>
          val icon = resources.icon(entry.tpe)
          val rowBounds = row.getBounds
          val iconBounds = icon.getBounds
          val offset = (rowBounds.height - iconBounds.height) / 2
          e.gc.drawImage(icon, rowBounds.x + offset, rowBounds.y + offset)
        case _ => // NOOP
      }
    })

    logTable.getColumns.filter(_.getWidth == 0).map(_.pack())
  }

  private def onWindowClose(closeEvent: Event): Unit = {
    import ConfigOptions.OnWindowClose._
    cfgMgr.getProperty(ConfigOptions.ActionOnWindowClose) match {
      case Undefined => promptWindowClose(closeEvent)
      case Close     => tryExit(closeEvent)
      case Minimize  => minimize(Some(closeEvent))
    }
  }

  private def promptWindowClose(closeEvent: Event): Unit = {
    import ConfigOptions._
    val lhm = new LinkedHashMap[String, Integer].withCode { lhm =>
      lhm.put(OnWindowClose.Minimize.prettyName, 1)
      lhm.put(OnWindowClose.Close.prettyName, 2)
      lhm.put("Cancel", -1)
    }
    // TODO: Extract into helper?
    val result = MessageDialogWithToggle.open(MessageDialog.CONFIRM, shell,
      "What to do?",
      "Choose an action on window close",
      "Remember my decision",
      true, null, null, SWT.NONE, lhm)
    val actionOption: Option[OnWindowClose] = result.getReturnCode match {
      case -1 => None
      case 1  => Some(OnWindowClose.Minimize)
      case 2  => Some(OnWindowClose.Close)
    }
    if (actionOption == None) {
      closeEvent.doit = false
    } else {
      val Some(action) = actionOption
      if (result.getToggleState) {
        cfgMgr.setProperty(ConfigOptions.ActionOnWindowClose, action)
      }
      (action: @unchecked) match {
        case OnWindowClose.Close    => tryExit(closeEvent)
        case OnWindowClose.Minimize => minimize(Some(closeEvent))
      }
    }
  }

  private def minimize(closeEventOption: Option[Event]): Unit = {
    closeEventOption foreach (_.doit = false)
    shell.setMinimized(true)
  }

  private def tryExit(closeEvent: Event): Unit = {
    def getRunningEntities(): Set[_ <: DownloadEntryView] = {
      downloadListMgr.list().filter(_.status == Status.Running)
    }
    try {
      val running = getRunningEntities()
      if (running.size > 0) {
        val confirmed = MessageDialog.openConfirm(shell, "What to do?",
          s"You have ${running.size} active download(s). Are you sure you wish to quit?")

        if (!confirmed) {
          closeEvent.doit = false
        } else {
          running foreach { de =>
            val pair = BackendManager.getCastedPair(de)
            pair.backend.downloader.stop(pair.de)
          }
          shell.setVisible(false)
          val terminatedNormally = waitUntil(() => getRunningEntities().size == 0, 2000)
          if (!terminatedNormally) {
            log.error("Couldn't stop all downloads before exiting")
          }
        }
      }
      if (closeEvent.doit) {
        downloadListMgr.save()
      }
    } catch {
      case ex: Exception =>
        log.error("Error terminating an application", ex)
        showError(shell, message = ex.getMessage)
    }
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
    row.setImage(0, resources.icon(de.status))
    mainColumnDefs.content.zipWithIndex.foreach {
      case (cd, i) => row.setText(i, cd.fmt(de))
    }
    if (de.supportsResumingOption == Some(false)) {
      // Would be better to add red-ish border, but that's non-trivial
      row.setForeground(new Color(display, 0x80, 0x00, 0x00))
    }
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
    val wasShowingLastRow =
      if (logTable.getItemCount > 0) {
        val prevLastRow = logTable.getItem(logTable.getItemCount - 1)
        isRowVisible(prevLastRow)
      } else true
    new TableItem(logTable, SWT.NONE).withCode { row =>
      row.setData(entry)
      row.setText(1, entry.date.toString(MainFrame.DateFmt))
      row.setText(2, entry.date.toString(MainFrame.TimeFmt))
      row.setText(3, lines.head.trim)
      row.setBackground(MainFrame.getLogColor(entry.tpe, display))
    }
    lines.tail.foreach { line =>
      new TableItem(logTable, SWT.NONE).withCode { row =>
        row.setText(3, line.trim)
        row.setBackground(MainFrame.getLogColor(entry.tpe, display))
      }
    }
    if (wasShowingLastRow) scrollTableToBottom(logTable)
  }

  private def updateButtonsEnabledState(): Unit = {
    btnStart.setEnabled(getSelectedDownloadEntries exists (_.status.canBeStarted))
    btnStop.setEnabled(getSelectedDownloadEntries exists (_.status.canBeStopped))
  }

  object downloadEntityFormat {
    def size(de: DownloadEntryView): String = {
      de.sizeOption map fmtSizePretty getOrElse ""
    }

    def downloadedSize(de: DownloadEntryView): String = {
      val downloadedSize = de.downloadedSize
      val prettyDownloadedSize = fmtSizePretty(downloadedSize)
      de.sizeOption match {
        case Some(totalSize) =>
          val percent = downloadedSize * 100 / totalSize
          percent + "% [" + prettyDownloadedSize + "]"
        case None if downloadedSize > 0 =>
          "[" + prettyDownloadedSize + "]"
        case _ =>
          ""
      }
    }

    private def fmtSizePretty(size: Long): String = {
      val groups = size.toString.reverse.grouped(3).map(_.reverse).toSeq.reverse
      groups.mkString("", " ", " B")
    }
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
        mainTable.deselectAll()
        mainTable.select(mainTable.getItems.indexOf(newRow))
        mainTable.showItem(newRow)
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
      if (!shell.isDisposed) display.syncExec { () =>
        if (!shell.isDisposed) code
      }
  }

  //
  // Helper classes
  //

  case class ColumnDef(name: String, width: Int = 0)

  case class ColumnDefExt[A](name: String, fmt: A => String, width: Int = 0) {
    def toColumnDef = ColumnDef(name, width)
  }

  class Columns[A](val content: ColumnDefExt[A]*) {
    def apply(i: Int): ColumnDefExt[A] = content(i)
  }
}

object MainFrame {
  val ProgressUpdateThresholdMs = 100

  val DateFmt = DateTimeFormat.forPattern("yyyy-MM-dd")
  val TimeFmt = DateTimeFormat.forPattern("HH:mm:ss")
  val DateTimeFmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  def getLogColor(tpe: LogEntry.Type, display: Display): Color = tpe match {
    case LogEntry.Info     => new Color(display, 0xE4, 0xF1, 0xFF)
    case LogEntry.Request  => new Color(display, 0xFF, 0xFF, 0xDD)
    case LogEntry.Response => new Color(display, 0xEB, 0xFD, 0xEB)
    case LogEntry.Error    => new Color(display, 0xFF, 0xDD, 0xDD)
  }
}
