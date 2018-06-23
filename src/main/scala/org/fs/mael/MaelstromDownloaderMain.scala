package org.fs.mael

import java.io.File

import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Shell
import org.fs.mael.backend.http.HttpBackend
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.config.ConfigStore
import org.fs.mael.core.config.FileBackedConfigStore
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.event.EventManagerImpl
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.core.list.DownloadListSerializerImpl
import org.fs.mael.core.migration.MigrationManager
import org.fs.mael.core.speed.SpeedTrackerImpl
import org.fs.mael.core.transfer.SimpleTransferManager
import org.fs.mael.core.transfer.TransferManager
import org.fs.mael.ui.MainFrame
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.resources.ResourcesImpl
import org.fs.utility.StopWatch
import org.slf4s.Logging

import javax.swing.JOptionPane

object MaelstromDownloaderMain extends App with Logging {

  //
  // Init code
  //

  log.info(BuildInfo.fullPrettyName)
  log.info("Application started, initializing...")

  val cfgFoler = new File("cfg")
  val globalCfgFile = new File(cfgFoler, "main.properties")
  val downloadListFile = new File(cfgFoler, "downloads.json")

  try {
    val shell: Shell = StopWatch.measureAndCall {
      preloadClasses()
      // TODO: Show minimal splash screen
      val globalCfg = new FileBackedConfigStore(globalCfgFile)
      val migrationMgr = new MigrationManager(globalCfg, downloadListFile)
      migrationMgr.apply()
      val display = new Display()
      val resources = new ResourcesImpl(display)
      val eventMgr = new EventManagerImpl
      val speedTracker = new SpeedTrackerImpl(eventMgr)
      eventMgr.subscribe(speedTracker)
      val backendMgr = new BackendManager
      val transferMgr = new SimpleTransferManager
      initBackends(backendMgr, transferMgr, globalCfg, eventMgr)
      val downloadListMgr = {
        val serializer = new DownloadListSerializerImpl
        new DownloadListManager(serializer, downloadListFile, eventMgr)
      }
      downloadListMgr.load()
      initUi(display, resources, globalCfg, backendMgr, downloadListMgr, eventMgr)
    }((_, ms) =>
      log.info(s"Init done in ${ms} ms"))

    uiLoop(shell)
  } catch {
    case th: Throwable =>
      JOptionPane.showMessageDialog(null, th.getMessage, "Error", JOptionPane.ERROR_MESSAGE)
      log.error("Uncaught error!", th)
  }

  //
  // Methods
  //

  /** Asynchronously reach out to some classes to force them to init eagerly */
  def preloadClasses(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Future
    Future {
      val nscalaTimeImports = com.github.nscala_time.time.Imports
      val universe = scala.reflect.runtime.universe
    }
  }

  def initBackends(
    backendMgr:  BackendManager,
    transferMgr: TransferManager,
    globalCfg:   ConfigStore,
    eventMgr:    EventManager
  ): Unit = {
    backendMgr += (new HttpBackend(transferMgr, globalCfg, eventMgr), 0)
  }

  def initUi(
    display:         Display,
    resources:       Resources,
    globalCfg:       ConfigStore,
    backendMgr:      BackendManager,
    downloadListMgr: DownloadListManager,
    eventMgr:        EventManager
  ): Shell = {
    val ui = new MainFrame(display, resources, globalCfg, backendMgr, downloadListMgr, eventMgr)
    ui.peer
  }

  def uiLoop(shell: Shell): Unit = {
    shell.open()
    shell.forceActive()
    val display = shell.getDisplay
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) display.sleep()
    }
    display.dispose()
    log.info("Application closed")
  }
}
