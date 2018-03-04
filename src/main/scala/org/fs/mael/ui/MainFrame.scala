package org.fs.mael.ui

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.dialogs.MessageDialogWithToggle
import org.eclipse.swt._
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.BuildInfo
import org.fs.mael.core.Status
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.event.UiSubscriber
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.components._
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.Hotkey
import org.fs.mael.ui.utils.Hotkey._
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

class MainFrame(
  shell:           Shell,
  resources:       Resources,
  cfgMgr:          ConfigManager,
  backendMgr:      BackendManager,
  downloadListMgr: DownloadListManager,
  eventMgr:        EventManager
) extends Logging {
  private val display = shell.getDisplay

  private var mainTable: DownloadsTable = _
  private var logTable: LogTable = _

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

    mainTable.init(downloadListMgr.list())
    mainTable.peer.setFocus()

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
    mainTable = new DownloadsTable(parent, resources)

    val menu = new Menu(mainTable.peer).withCode { menu =>
      val parent = mainTable.peer
      parent.setMenu(menu)

      createMenuItem(menu, "Open folder", parent, None) {
        openFolders()
      }

      createMenuItem(menu, "Copy download URI", parent, Some(Hotkey(Ctrl, Key('C')))) {
        copyUris()
      }

      createMenuItem(menu, "Delete", parent, Some(Hotkey(Key.Delete))) {
        if (mainTable.peer.getSelectionCount > 0) {
          tryDeleteSelectedDownloads()
        }
      }

      // TODO: Delete with file
      // TODO: Restart
      // TODO: Properties
    }

    mainTable.peer.addListener(SWT.Selection, e => {
      logTable.render(mainTable.selectedEntryOption)
    })
    mainTable.peer.addListener(SWT.Selection, e => updateButtonsEnabledState())
  }

  private def createDetailsPanel(parent: Composite): Unit = {
    logTable = new LogTable(parent, resources)
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
    import java.util.LinkedHashMap
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
          val terminatedNormally = waitUntil(2000) { getRunningEntities().size == 0 }
          if (!terminatedNormally) {
            log.error("Couldn't stop all downloads before exiting")
          }
        }
      }
      if (closeEvent.doit) {
        downloadListMgr.save()
        shell.dispose()
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

  private def updateButtonsEnabledState(): Unit = {
    btnStart.setEnabled(mainTable.selectedEntries exists (_.status.canBeStarted))
    btnStop.setEnabled(mainTable.selectedEntries exists (_.status.canBeStopped))
  }

  private def clipboard = Toolkit.getDefaultToolkit.getSystemClipboard

  //
  // Subscriber trait
  //

  object subscriber extends UiSubscriber {
    override val subscriberId: String = "swt-ui"

    // TODO: Make per-download?
    val ProgressUpdateThresholdMs = 100

    import org.fs.mael.core.event.EventForUi
    import org.fs.mael.core.event.Events._

    def fired(event: EventForUi): Unit = event match {
      case Added(de) => syncExecSafely {
        if (!shell.isDisposed) {
          mainTable.add(de)
          logTable.render(mainTable.selectedEntryOption)
          updateButtonsEnabledState()
        }
      }

      case Removed(de) => syncExecSafely {
        mainTable.remove(de)
        updateButtonsEnabledState()
      }

      case StatusChanged(de, prevStatus) => syncExecSafely {
        // Full row update
        mainTable.update(de)
        updateButtonsEnabledState()
      }

      case Progress(de) =>
        // We assume this is only called by event manager processing thread, so no additional sync needed
        if (System.currentTimeMillis() - lastProgressUpdateTS > ProgressUpdateThresholdMs) {
          syncExecSafely {
            // TODO: Optimize
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

    /** Execute code in UI thread iff UI is not disposed yet */
    private def syncExecSafely(code: => Unit): Unit =
      if (!shell.isDisposed) display.syncExec { () =>
        if (!shell.isDisposed) code
      }
  }
}
