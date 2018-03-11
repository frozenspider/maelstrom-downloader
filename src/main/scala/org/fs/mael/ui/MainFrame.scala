package org.fs.mael.ui

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

import scala.util.Random

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.dialogs.MessageDialogWithToggle
import org.eclipse.swt._
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.events.ShellAdapter
import org.eclipse.swt.events.ShellEvent
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.BuildInfo
import org.fs.mael.core.Status
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.event.EventForUi
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.event.Events._
import org.fs.mael.core.event.UiSubscriber
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.components._
import org.fs.mael.ui.prefs.GlobalPreferences
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.Hotkey
import org.fs.mael.ui.utils.Hotkey._
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging
import org.fs.mael.core.entry.DownloadEntry

class MainFrame(
  display:         Display,
  resources:       Resources,
  globalCfgMgr:    ConfigManager,
  backendMgr:      BackendManager,
  downloadListMgr: DownloadListManager,
  eventMgr:        EventManager
) extends Logging {

  private val trayOption = Option(display.getSystemTray)
  private var trayItem: TrayItem = _
  private var mainTable: DownloadsTable = _
  private var logTable: LogTable = _

  private var btnStart: ToolItem = _
  private var btnStop: ToolItem = _

  /** When the download progress update was rendered for the last time, used to avoid excessive load */
  private var lastProgressUpdateTS: Long = System.currentTimeMillis

  val peer: Shell = new Shell(display).withCode { peer =>
    peer.addListener(SWT.Close, onWindowClose)
    peer.addShellListener(new ShellAdapter {
      override def shellIconified(e: ShellEvent): Unit = {
        // Note: this might be called second time from minimize, but that's not a problem
        e.doit = false
        minimize(None)
      }
    })

    // Layout

    peer.setLayout(new FillLayout(SWT.VERTICAL).withCode { layout =>
      layout.spacing = 0
    })

    val menu = new Menu(peer, SWT.BAR)

    val group = new Composite(peer, SWT.NONE)
    group.setLayout(new GridLayout().withCode { layout =>
      layout.horizontalSpacing = 0
      layout.verticalSpacing = 0
      layout.marginWidth = 0
      layout.marginHeight = 0
    })

    val toolbar = new ToolBar(group, SWT.FLAT)

    val sashForm = new SashForm(group, SWT.VERTICAL)
    sashForm.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true))

    mainTable = new DownloadsTable(sashForm, resources, globalCfgMgr)
    logTable = new LogTable(sashForm, resources)

    sashForm.setWeights(Array(10, 10))

    // Init

    trayOption foreach (tray => initTray(tray, peer))

    fillMenu(menu, peer)
    fillToolbar(toolbar, peer)
    fillMainTable(mainTable, peer)

    mainTable.init(downloadListMgr.list())
    mainTable.peer.setFocus()

    peer.setImage(resources.mainIcon)
    peer.setText(BuildInfo.fullPrettyName)
    peer.setSize(1000, 600)
    centerOnScreen(peer)

    eventMgr.subscribe(subscriber)
  }

  private def initTray(tray: Tray, shell: Shell): Unit = {
    trayItem = new TrayItem(tray, SWT.NONE)
    trayItem.setImage(resources.mainIcon)
    trayItem.setToolTipText(BuildInfo.prettyName)

    import GlobalPreferences._
    globalCfgMgr.addSettingChangedListener(ShowTrayIconBehavior)(e => {
      updateTrayIconVisibility(e.newValue)
    })
    updateTrayIconVisibility(globalCfgMgr(ShowTrayIconBehavior))
    def show(e: Event): Unit = {
      updateTrayIconVisibility(globalCfgMgr(ShowTrayIconBehavior))
      shell.setVisible(true)
      shell.setMinimized(false)
      shell.forceActive()
    }
    trayItem.addListener(SWT.Selection, show)

    // TODO: Add more items
    val menu = new Menu(shell, SWT.POP_UP)
    createMenuItem(menu, "Show main window", shell, None)(show).withCode { item =>
      menu.setDefaultItem(item)
    }
    new MenuItem(menu, SWT.SEPARATOR)
    createMenuItem(menu, "Exit", shell, None)(tryExit)

    trayItem.addListener(SWT.MenuDetect, e => menu.setVisible(true))
  }

  private def updateTrayIconVisibility(setting: GlobalPreferences.ShowTrayIcon): Unit = {
    val showWhenNeeded = setting == GlobalPreferences.ShowTrayIcon.WhenNeeded
    trayItem.setVisible(!showWhenNeeded)
  }

  private def fillMenu(menu: Menu, shell: Shell): Unit = {
    val parent = menu.getParent
    parent.setMenuBar(menu)

    new MenuItem(menu, SWT.CASCADE).withCode { menuItem =>
      menuItem.setText("&File")

      val submenu = new Menu(parent, SWT.DROP_DOWN)
      menuItem.setMenu(submenu)

      val itemExit = new MenuItem(submenu, SWT.PUSH)
      itemExit.setText("Exit")
      itemExit.addListener(SWT.Selection, tryExit)
    }

    new MenuItem(menu, SWT.CASCADE).withCode { menuItem =>
      menuItem.setText("&Service")

      val submenu = new Menu(parent, SWT.DROP_DOWN)
      menuItem.setMenu(submenu)

      val itemOptions = new MenuItem(submenu, SWT.PUSH)
      itemOptions.setText("Options")
      itemOptions.addListener(SWT.Selection, e => new GlobalPreferences(globalCfgMgr).showDialog(shell))
    }
  }

  private def fillToolbar(toolbar: ToolBar, shell: Shell): Unit = {
    val btnAdd = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnAdd =>
      btnAdd.setText("Add")
      btnAdd.addListener(SWT.Selection, e => {
        tryShowingError(peer, log) {
          val dialog = new EditDownloadDialog(None, shell, resources, globalCfgMgr, backendMgr, downloadListMgr, eventMgr)
          dialog.peer.open()
        }
      })
    }

    btnStart = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStart =>
      btnStart.setText("Start")
      btnStart.addListener(SWT.Selection, e => {
        mainTable.selectedEntries map { dev =>
          tryShowingError(peer, log) {
            val backend = backendMgr(dev.backendId)
            val de = dev.asInstanceOf[DownloadEntry]
            backend.downloader.start(de, globalCfgMgr(GlobalPreferences.NetworkTimeout))
          }
        }
      })
      btnStart.forDownloads(_ exists (_.status.canBeStarted))
    }

    btnStop = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStop =>
      btnStop.setText("Stop")
      btnStop.addListener(SWT.Selection, e => {
        mainTable.selectedEntries map { dev =>
          tryShowingError(peer, log) {
            val backend = backendMgr(dev.backendId)
            val de = dev.asInstanceOf[DownloadEntry]
            backend.downloader.stop(de)
          }
        }
      })
      btnStop.forDownloads(_ exists (_.status.canBeStopped))
    }

    toolbar.pack()
    toolbar.setLayoutData((new GridData()).withCode { gridData =>
      gridData.horizontalAlignment = GridData.FILL
      gridData.grabExcessHorizontalSpace = true
    })
  }

  private def fillMainTable(mainTable: DownloadsTable, shell: Shell): Unit = {
    val menu = new Menu(mainTable.peer).withCode { menu =>
      val parent = mainTable.peer
      parent.setMenu(menu)

      createMenuItem(menu, "Open folder", parent, None) { e =>
        openFolders()
      }

      createMenuItem(menu, "Copy download URI", parent, Some(Hotkey(Ctrl, Key('C')))) { e =>
        copyUris()
      }

      new MenuItem(menu, SWT.SEPARATOR)

      createMenuItem(menu, "Delete", parent, Some(Hotkey(Key.Delete))) { e =>
        if (mainTable.peer.getSelectionCount > 0) {
          tryDeleteSelectedDownloads(false)
        }
      }

      createMenuItem(menu, "Delete with file", parent, Some(Hotkey(Shift, Key.Delete))) { e =>
        if (mainTable.peer.getSelectionCount > 0) {
          tryDeleteSelectedDownloads(true)
        }
      }

      new MenuItem(menu, SWT.SEPARATOR)

      val openProps = createMenuItem(menu, "Properties", parent, None) { e =>
        tryShowingError(peer, log) {
          val deOption = mainTable.selectedEntryOption
          require(deOption.isDefined)
          val dialog = new EditDownloadDialog(deOption, shell, resources, globalCfgMgr, backendMgr, downloadListMgr, eventMgr)
          dialog.peer.open()
        }
      }.forSingleDownloads()
      mainTable.peer.addListener(SWT.MouseDoubleClick, e => openProps.notifyListeners(SWT.Selection, e))

      // TODO: Restart
    }

    mainTable.peer.addListener(SWT.Selection, e => {
      val selectedOption = mainTable.selectedEntryOption
      if (selectedOption != logTable.currentOption) {
        logTable.render(mainTable.selectedEntryOption)
      }
    })
  }

  private def onWindowClose(closeEvent: Event): Unit = {
    import org.fs.mael.ui.prefs.GlobalPreferences.OnWindowClose._
    globalCfgMgr(GlobalPreferences.OnWindowCloseBehavior) match {
      case Undefined => promptWindowClose(closeEvent)
      case Close     => tryExit(closeEvent)
      case Minimize  => minimize(Some(closeEvent))
    }
  }

  private def promptWindowClose(closeEvent: Event): Unit = {
    import java.util.LinkedHashMap
    import org.fs.mael.ui.prefs.GlobalPreferences._
    val lhm = new LinkedHashMap[String, Integer].withCode { lhm =>
      lhm.put(OnWindowClose.Minimize.prettyName, 1)
      lhm.put(OnWindowClose.Close.prettyName, 2)
      lhm.put("Cancel", -1)
    }
    // TODO: Extract into helper?
    val result = MessageDialogWithToggle.open(MessageDialog.CONFIRM, peer,
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
        globalCfgMgr.set(OnWindowCloseBehavior, action)
      }
      (action: @unchecked) match {
        case OnWindowClose.Close    => tryExit(closeEvent)
        case OnWindowClose.Minimize => minimize(Some(closeEvent))
      }
    }
  }

  private def minimize(closeEventOption: Option[Event]): Unit = {
    closeEventOption foreach (_.doit = false)
    import org.fs.mael.ui.prefs.GlobalPreferences._
    // Only minimize to tray if tray exists
    (closeEventOption, globalCfgMgr(MinimizeToTrayBehavior)) match {
      case (_, MinimizeToTray.Always) if trayOption.isDefined        => minimizeToTray()
      case (Some(_), MinimizeToTray.OnClose) if trayOption.isDefined => minimizeToTray()
      case _                                                         => peer.setMinimized(true)
    }
  }

  private def minimizeToTray(): Unit = {
    require(trayOption.isDefined, "Can't minimize to non-existent tray!")
    trayItem.setVisible(true)
    peer.setVisible(false)
  }

  private def tryExit(closeEvent: Event): Unit = {
    def getRunningEntities(): Seq[_ <: DownloadEntryView] = {
      downloadListMgr.list().filter(_.status == Status.Running)
    }
    try {
      val running = getRunningEntities()
      if (running.size > 0) {
        val confirmed = MessageDialog.openConfirm(peer, "Confirmation",
          s"You have ${running.size} active download(s). Are you sure you wish to quit?")

        if (!confirmed) {
          closeEvent.doit = false
        } else {
          running foreach { dev =>
            val backend = backendMgr(dev.backendId)
            val de = dev.asInstanceOf[DownloadEntry]
            backend.downloader.stop(de)
          }
          peer.setVisible(false)
          val terminatedNormally = waitUntil(2000) { getRunningEntities().size == 0 }
          if (!terminatedNormally) {
            log.error("Couldn't stop all downloads before exiting")
          }
        }
      }
      if (closeEvent.doit) {
        trayOption foreach { _ =>
          // It was sometimes left visible in system tray after termination
          trayItem.dispose()
        }
        downloadListMgr.save()
        peer.dispose()
      }
    } catch {
      case ex: Exception =>
        log.error("Error terminating an application", ex)
        showError(peer, message = ex.getMessage)
    }
  }

  private def tryDeleteSelectedDownloads(withFile: Boolean): Unit = {
    val msg = "Are you sure you wish to delete selected downloads" +
      (if (withFile) " AND thier corresponding files?" else "?")
    val confirmed = MessageDialog.openConfirm(peer, "Confirmation", msg)
    if (confirmed) {
      val selected = mainTable.selectedEntries
      downloadListMgr.removeAll(selected)
      if (withFile) {
        selected foreach (_.fileOption foreach (_.delete()))
      }
    }
  }

  private def copyUris(): Unit = {
    val selected = mainTable.selectedEntries
    val uris = selected.map(_.uri)
    val content = new StringSelection(uris.mkString("\n"))
    clipboard.setContents(content, null)
  }

  private def openFolders(): Unit = {
    val selected = mainTable.selectedEntries
    val locations = selected.map(_.location).distinct
    locations foreach Desktop.getDesktop.open
  }

  private def clipboard = Toolkit.getDefaultToolkit.getSystemClipboard

  /** Execute code in UI thread iff UI is not disposed yet */
  private def syncExecSafely(code: => Unit): Unit =
    if (!peer.isDisposed) display.syncExec { () =>
      if (!peer.isDisposed) code
    }

  //
  // Subscriber trait
  //

  object subscriber extends UiSubscriber {
    override val subscriberId: String = "swt-ui"

    // TODO: Make per-download?
    val ProgressUpdateThresholdMs = 100

    override def fired(event: EventForUi): Unit = event match {
      case Added(de) => syncExecSafely {
        if (!peer.isDisposed) {
          mainTable.add(de)
        }
      }

      case Removed(de) => syncExecSafely {
        mainTable.remove(de)
      }

      case StatusChanged(de, prevStatus) => syncExecSafely {
        // Full row update
        mainTable.update(de)
      }

      case Progress(de) =>
        // We assume this is only called by event manager processing thread, so no additional sync needed
        if (System.currentTimeMillis() - lastProgressUpdateTS > ProgressUpdateThresholdMs) {
          syncExecSafely {
            // TODO: Optimize?
            mainTable.update(de)
          }
          lastProgressUpdateTS = System.currentTimeMillis()
        }

      case DetailsChanged(de) => syncExecSafely {
        mainTable.update(de)
      }

      case Logged(de, entry) => syncExecSafely {
        if (mainTable.selectedEntryOption == Some(de)) {
          logTable.append(entry, false)
        }
      }
    }
  }

  private implicit class ItemExt[T <: { def setEnabled(b: Boolean): Unit }](item: T) {
    def forSingleDownloads(): T = {
      mainTable.peer.addListener(SWT.Selection, e => {
        item.setEnabled(mainTable.peer.getSelectionCount == 1)
      })
      item
    }

    def forDownloads(condition: Seq[DownloadEntryView] => Boolean): T = {
      mainTable.peer.addListener(SWT.Selection, e => {
        item.setEnabled(condition(mainTable.selectedEntries))
      })
      eventMgr.subscribe(new UiSubscriber {
        override val subscriberId = "_event_forDownloads_" + Random.alphanumeric.take(20).mkString
        override def fired(event: EventForUi): Unit = event match {
          case StatusChanged(_, _) => syncExecSafely(item.setEnabled(condition(mainTable.selectedEntries)))
          case _                   => // NOOP
        }
      })
      item
    }
  }
}
