package org.fs.mael.ui

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

import scala.collection.mutable.WeakHashMap
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
import org.fs.mael.core.config.ConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventForUi
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.event.Events._
import org.fs.mael.core.event.UiSubscriber
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.components._
import org.fs.mael.ui.config.GlobalSettings
import org.fs.mael.ui.config.GlobalSettingsController
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.Hotkey
import org.fs.mael.ui.utils.Hotkey._
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

class MainFrame(
  display:         Display,
  resources:       Resources,
  globalCfg:       ConfigStore,
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

  val peer: Shell = new Shell(display).withCode { peer =>
    peer.addListener(SWT.Close, actions.onWindowClose)
    peer.addShellListener(new ShellAdapter {
      override def shellIconified(e: ShellEvent): Unit = {
        // Note: this might be called second time from minimize, but that's not a problem
        e.doit = false
        actions.minimizeClicked()
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
    sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))

    mainTable = new DownloadsTable(sashForm, resources, globalCfg)
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

    import org.fs.mael.ui.config.GlobalSettings._
    globalCfg.addSettingChangedListener(ShowTrayIconBehavior)(e => {
      updateTrayIconVisibility(e.newValue)
    })
    updateTrayIconVisibility(globalCfg(ShowTrayIconBehavior))
    def show(e: Event): Unit = {
      updateTrayIconVisibility(globalCfg(ShowTrayIconBehavior))
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
    createMenuItem(menu, "Exit", shell, None)(actions.exitClicked)

    trayItem.addListener(SWT.MenuDetect, e => menu.setVisible(true))
  }

  private def updateTrayIconVisibility(setting: GlobalSettings.ShowTrayIcon): Unit = {
    val showWhenNeeded = setting == GlobalSettings.ShowTrayIcon.WhenNeeded
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
      itemExit.addListener(SWT.Selection, actions.exitClicked)
    }

    new MenuItem(menu, SWT.CASCADE).withCode { menuItem =>
      menuItem.setText("&Service")

      val submenu = new Menu(parent, SWT.DROP_DOWN)
      menuItem.setMenu(submenu)

      val itemSettings = new MenuItem(submenu, SWT.PUSH)
      itemSettings.setText("Settings")
      itemSettings.addListener(SWT.Selection, e => new GlobalSettingsController(globalCfg).showDialog(shell))
    }
  }

  private def fillToolbar(toolbar: ToolBar, shell: Shell): Unit = {
    val btnAdd = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnAdd =>
      btnAdd.setText("Add")
      btnAdd.addListener(SWT.Selection, e => {
        tryShowingError(peer, log) {
          val dialog = new EditDownloadDialog(None, shell, resources, globalCfg, backendMgr, downloadListMgr, eventMgr)
          dialog.shell.open()
        }
      })
    }

    btnStart = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStart =>
      btnStart.setText("Start")
      btnStart.addListener(SWT.Selection, e => {
        mainTable.selectedEntries map { de =>
          tryShowingError(peer, log) {
            val backend = backendMgr(de.backendId)
            backend.downloader.start(de, globalCfg(GlobalSettings.NetworkTimeout))
          }
        }
      })
      btnStart.forDownloads(_ exists (_.status.canBeStarted))
    }

    btnStop = (new ToolItem(toolbar, SWT.PUSH)).withCode { btnStop =>
      btnStop.setText("Stop")
      btnStop.addListener(SWT.Selection, e => {
        mainTable.selectedEntries map { de =>
          tryShowingError(peer, log) {
            val backend = backendMgr(de.backendId)
            backend.downloader.stop(de)
          }
        }
      })
      btnStop.forDownloads(_ exists (_.status.canBeStopped))
    }

    toolbar.pack()
    toolbar.setLayoutData((new GridData()).withCode { gridData =>
      gridData.horizontalAlignment = SWT.FILL
      gridData.grabExcessHorizontalSpace = true
    })
  }

  private def fillMainTable(mainTable: DownloadsTable, shell: Shell): Unit = {
    val menu = new Menu(mainTable.peer).withCode { menu =>
      val parent = mainTable.peer
      parent.setMenu(menu)

      createMenuItem(menu, "Restart download", parent, None) { e =>
        actions.restartClicked()
      }.forDownloads(_ exists (_.status != Status.Running))

      new MenuItem(menu, SWT.SEPARATOR)

      createMenuItem(menu, "Open folder", parent, None) { e =>
        actions.openFoldersClicked()
      }

      createMenuItem(menu, "Copy download URI", parent, Some(Hotkey(Ctrl, Key('C')))) { e =>
        actions.copyUrisClicked()
      }

      new MenuItem(menu, SWT.SEPARATOR)

      createMenuItem(menu, "Delete", parent, Some(Hotkey(Key.Delete))) { e =>
        actions.deleteClicked(false)
      }

      createMenuItem(menu, "Delete with file", parent, Some(Hotkey(Shift, Key.Delete))) { e =>
        actions.deleteClicked(true)
      }

      new MenuItem(menu, SWT.SEPARATOR)

      val openProps = createMenuItem(menu, "Properties", parent, None) { e =>
        tryShowingError(peer, log) {
          val deOption = mainTable.selectedEntryOption
          require(deOption.isDefined)
          val dialog = new EditDownloadDialog(deOption, shell, resources, globalCfg, backendMgr, downloadListMgr, eventMgr)
          dialog.shell.open()
        }
      }.forSingleDownloads()
      mainTable.peer.addListener(SWT.MouseDoubleClick, e => openProps.notifyListeners(SWT.Selection, e))
    }

    mainTable.peer.addListener(SWT.Selection, e => {
      val selectedOption = mainTable.selectedEntryOption
      if (selectedOption != logTable.currentOption) {
        logTable.render(mainTable.selectedEntryOption)
      }
    })
  }

  private def clipboard = Toolkit.getDefaultToolkit.getSystemClipboard

  private object actions {
    def restartClicked(): Unit = {
      val msg = "Are you sure you wish to restart selected downloads from the beginning?" +
        "\nYou will lose all current progress on it!"
      val confirmed = MessageDialog.openConfirm(peer, "Confirmation", msg)
      if (confirmed) {
        val selected = mainTable.selectedEntries filter (_.status != Status.Running)
        selected foreach { de =>
          val backend = backendMgr(de.backendId)
          backend.downloader.restart(de, globalCfg(GlobalSettings.NetworkTimeout))
        }
      }
    }

    def deleteClicked(withFile: Boolean): Unit = {
      if (mainTable.peer.getSelectionCount > 0) { // TODO: Embed this filter into action?
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
    }

    def copyUrisClicked(): Unit = {
      val selected = mainTable.selectedEntries
      val uris = selected.map(_.uri)
      val content = new StringSelection(uris.mkString("\n"))
      clipboard.setContents(content, null)
    }

    def openFoldersClicked(): Unit = {
      val selected = mainTable.selectedEntries
      val locations = selected.map(_.location).distinct
      locations foreach Desktop.getDesktop.open
      // TODO: Select files?
    }

    def onWindowClose(closeEvent: Event): Unit = {
      import org.fs.mael.ui.config.GlobalSettings.OnWindowClose._
      globalCfg(GlobalSettings.OnWindowCloseBehavior) match {
        case Undefined => promptWindowClose(closeEvent)
        case Close     => exit(closeEvent)
        case Minimize  => minimize(Some(closeEvent))
      }
    }

    private def promptWindowClose(closeEvent: Event): Unit = {
      import java.util.LinkedHashMap
      import org.fs.mael.ui.config.GlobalSettings._
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
          globalCfg.set(OnWindowCloseBehavior, action)
        }
        (action: @unchecked) match {
          case OnWindowClose.Close    => exit(closeEvent)
          case OnWindowClose.Minimize => minimize(Some(closeEvent))
        }
      }
    }

    def minimizeClicked(): Unit = {
      minimize(None)
    }

    private def minimize(closeEventOption: Option[Event]): Unit = {
      closeEventOption foreach (_.doit = false)
      import org.fs.mael.ui.config.GlobalSettings._
      // Only minimize to tray if tray exists
      (closeEventOption, globalCfg(MinimizeToTrayBehavior)) match {
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

    def exitClicked(closeEvent: Event): Unit = {
      exit(closeEvent)
    }

    private def exit(closeEvent: Event): Unit = {
      def getRunningEntities(): Seq[DownloadEntry] = {
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
            running foreach { de =>
              val backend = backendMgr(de.backendId)
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
          // TODO: Doesn't work! Need another workaround
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
  }

  //
  // Subscriber trait
  //

  private object subscriber extends UiSubscriber {
    override val subscriberId: String = "swt-ui"

    val ProgressUpdateThresholdMs = 100
    val SpeedUpdateThresholdMs = 100

    /** When the download progress update was rendered for the last time, used to avoid excessive load */
    private val lastProgressUpdateTS: WeakHashMap[DownloadEntry, Long] = WeakHashMap.empty
    /** When the download speed/ETA update was rendered for the last time, used to avoid excessive load */
    private val lastSpeedUpdateTS: WeakHashMap[DownloadEntry, Long] = WeakHashMap.empty

    override def fired(event: EventForUi): Unit = event match {
      case Added(de) => syncExecSafely(peer) {
        if (!peer.isDisposed) {
          mainTable.add(de)
        }
      }

      case Removed(de) => syncExecSafely(peer) {
        mainTable.remove(de)
      }

      case StatusChanged(de, prevStatus) => syncExecSafely(peer) {
        // Full row update
        mainTable.update(de)
      }

      case DetailsChanged(de) => syncExecSafely(peer) {
        mainTable.update(de)
      }

      case Logged(de, entry) => syncExecSafely(peer) {
        if (mainTable.selectedEntryOption == Some(de)) {
          logTable.append(entry, false)
        }
      }

      case Progress(de) =>
        updateTableDetailsWithThreshold(de)

      case SpeedEta(de, speedOption, etaSecondsOption) =>
        updateTableSpeedWithThreshold(de, speedOption, etaSecondsOption)
    }

    private def updateTableDetailsWithThreshold(de: DownloadEntry): Unit = {
      // We assume this is only called by event manager processing thread, so no additional sync needed
      if (System.currentTimeMillis() - lastProgressUpdateTS.getOrElse(de, 0L) > ProgressUpdateThresholdMs) {
        syncExecSafely(peer) {
          // TODO: Optimize, updating only specific columns?
          mainTable.update(de)
        }
        lastProgressUpdateTS(de) = System.currentTimeMillis()
      }
    }

    private def updateTableSpeedWithThreshold(de: DownloadEntry, speedOption: Option[Long], etaSecondsOption: Option[Long]): Unit = {
      // We assume this is only called by event manager processing thread, so no additional sync needed
      if (System.currentTimeMillis() - lastSpeedUpdateTS.getOrElse(de, 0L) > SpeedUpdateThresholdMs) {
        syncExecSafely(peer) {
          mainTable.updateSpeedEta(de, speedOption, etaSecondsOption)
        }
        lastSpeedUpdateTS(de) = System.currentTimeMillis()
      }
    }
  }

  import scala.language.reflectiveCalls
  private implicit class ItemExt[T <: { def setEnabled(b: Boolean): Unit }](item: T) {
    def forSingleDownloads(): T = {
      mainTable.peer.addListener(SWT.Selection, e => {
        item.setEnabled(mainTable.peer.getSelectionCount == 1)
      })
      item
    }

    def forDownloads(condition: Seq[DownloadEntry] => Boolean): T = {
      mainTable.peer.addListener(SWT.Selection, e => {
        item.setEnabled(condition(mainTable.selectedEntries))
      })
      eventMgr.subscribe(new UiSubscriber {
        override val subscriberId = "_event_forDownloads_" + Random.alphanumeric.take(20).mkString
        override def fired(event: EventForUi): Unit = event match {
          case StatusChanged(_, _) => syncExecSafely(peer)(item.setEnabled(condition(mainTable.selectedEntries)))
          case _                   => // NOOP
        }
      })
      item
    }
  }
}
