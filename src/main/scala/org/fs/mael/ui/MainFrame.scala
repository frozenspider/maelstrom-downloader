package org.fs.mael.ui

import java.awt.Desktop
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
import org.fs.mael.ui.components.DownloadsTable
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

import com.github.nscala_time.time.Imports._

class MainFrame(
  shell:           Shell,
  resources:       Resources,
  cfgMgr:          ConfigManager,
  backendMgr:      BackendManager,
  downloadListMgr: DownloadListManager,
  eventMgr:        EventManager
) extends Logging {
  private val display = shell.getDisplay
  private val logColumnHeaders = Seq(ColumnDef("", 24), ColumnDef("Date", 80), ColumnDef("Time", 80), ColumnDef("Information", 500))

  private var mainTable: DownloadsTable = _
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

    mainTable.renderDownloads(downloadListMgr.list())

    adjustColumnWidths(mainTable.component)
    mainTable.component.setFocus()
    shell.setImage(resources.mainIcon)
    shell.setText(BuildInfo.fullPrettyName)
    shell.setSize(1000, 600)
    centerOnScreen(shell)

    eventMgr.subscribe(subscriber)
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
        new AddDownloadFrame(dialog, cfgMgr, backendMgr, downloadListMgr)
        dialog.open()
      })
    }

    btnStart = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStart =>
      btnStart.setText("Start")
      btnStart.setEnabled(false)
      btnStart.addListener(SWT.Selection, e => {
        mainTable.selectedEntries map { de =>
          val pair = backendMgr.getCastedPair(de)
          pair.backend.downloader.start(pair.de, cfgMgr.getProperty(ConfigOptions.NetworkTimeout))
        }
      })
    }

    btnStop = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStop =>
      btnStop.setText("Stop")
      btnStop.setEnabled(false)
      btnStop.addListener(SWT.Selection, e => {
        mainTable.selectedEntries map { de =>
          val pair = backendMgr.getCastedPair(de)
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
    mainTable = new DownloadsTable(parent, resources, MainFrame.DateTimeFmt)

    val menu = new Menu(mainTable.component).withCode { menu =>
      val itemDelete = new MenuItem(menu, SWT.NONE)
      itemDelete.setText("Delete")
      itemDelete.addListener(SWT.Selection, e => tryDeleteSelectedDownloads())

      val itemOpenFolder = new MenuItem(menu, SWT.NONE)
      itemOpenFolder.setText("Open folder")
      itemOpenFolder.addListener(SWT.Selection, e => openFolders())

      // TODO: Delete with file
      // TODO: Restart
      // TODO: Properties
    }
    mainTable.component.setMenu(menu)

    mainTable.component.addKeyListener(keyPressed {
      case e if e.keyCode == SWT.DEL && mainTable.component.getSelectionCount > 0 =>
        tryDeleteSelectedDownloads()
    })
    mainTable.component.addListener(SWT.Selection, e => {
      mainTable.selectedEntryOption map renderDownloadLog getOrElse { logTable.removeAll() }
    })
    mainTable.component.addListener(SWT.Selection, e => updateButtonsEnabledState())
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
        val confirmed = MessageDialog.openConfirm(shell, "Confirmation",
          s"You have ${running.size} active download(s). Are you sure you wish to quit?")

        if (!confirmed) {
          closeEvent.doit = false
        } else {
          running foreach { de =>
            val pair = backendMgr.getCastedPair(de)
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

  private def tryDeleteSelectedDownloads(): Unit = {
    val confirmed = MessageDialog.openConfirm(shell, "Confirmation",
      s"Are you sure you wish to delete selected downloads?")
    if (confirmed) {
      val selected = mainTable.selectedEntries
      downloadListMgr.removeAll(selected)
    }
  }

  private def openFolders(): Unit = {
    val selected = mainTable.selectedEntries
    val locations = selected.map(_.location).distinct
    locations foreach Desktop.getDesktop.open
  }

  private def adjustColumnWidths(table: Table): Unit = {
    table.getColumns.filter(_.getWidth == 0).map(_.pack())
  }

  private def renderDownloadLog(de: DownloadEntryView): Unit = {
    logTable.removeAll()
    de.downloadLog.foreach(appendDownloadLogEntry(_, true))
    scrollTableToBottom(logTable)
  }

  private def appendDownloadLogEntry(entry: LogEntry, dontScroll: Boolean): Unit = {
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
    if (!dontScroll && wasShowingLastRow) scrollTableToBottom(logTable)
  }

  private def updateButtonsEnabledState(): Unit = {
    btnStart.setEnabled(mainTable.selectedEntries exists (_.status.canBeStarted))
    btnStop.setEnabled(mainTable.selectedEntries exists (_.status.canBeStopped))
  }

  //
  // Subscriber trait
  //

  object subscriber extends UiSubscriber {
    override val subscriberId: String = "swt-ui"

    override def added(de: DownloadEntryView): Unit = syncExecSafely {
      if (!shell.isDisposed) {
        mainTable.add(de)
        mainTable.selectedEntryOption map renderDownloadLog getOrElse { logTable.removeAll() }
        updateButtonsEnabledState()
      }
    }

    override def removed(de: DownloadEntryView): Unit = syncExecSafely {
      mainTable.remove(de)
      updateButtonsEnabledState()
    }

    override def statusChanged(de: DownloadEntryView, prevStatus: Status): Unit = syncExecSafely {
      // Full row update
      mainTable.update(de)
      updateButtonsEnabledState()
    }

    override def progress(de: DownloadEntryView): Unit = {
      // We assume this is only called by event manager processing thread, so no additional sync needed
      if (System.currentTimeMillis() - lastProgressUpdateTS > MainFrame.ProgressUpdateThresholdMs) {
        syncExecSafely {
          // TODO: Optimize
          mainTable.update(de)
        }
        lastProgressUpdateTS = System.currentTimeMillis()
      }
    }

    override def detailsChanged(de: DownloadEntryView): Unit = syncExecSafely {
      mainTable.update(de)
    }

    override def logged(de: DownloadEntryView, entry: LogEntry): Unit = syncExecSafely {
      if (mainTable.selectedEntryOption == Some(de)) {
        appendDownloadLogEntry(entry, false)
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
