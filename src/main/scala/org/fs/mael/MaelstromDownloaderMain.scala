package org.fs.mael

import java.io.File

import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Shell
import org.fs.mael.backend.http.HttpBackend
import org.fs.mael.core.CoreUtils._
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.core.list.DownloadListSerializer
import org.fs.mael.ui.ConfigManager
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

  val downloadListFile = new File("downloads.json")

  try {
    val shell: Shell = StopWatch.measureAndCall {
      // TODO: Show minimal splash screen
      val display = new Display()
      val cfgMgr = new ConfigManager
      val resources = new ResourcesImpl(display)
      val downloadListMgr = {
        val serializer = new DownloadListSerializer
        new DownloadListManager(serializer, downloadListFile)
      }
      preloadClasses()
      initServices()
      downloadListMgr.load()
      initUi(display, resources, cfgMgr, downloadListMgr)
    }((_, ms) =>
      log.info(s"Init done in ${ms} ms"))

    uiLoop(shell)
  } catch {
    case th: Throwable =>
      JOptionPane.showMessageDialog(null, "An error occurred: " + th.getMessage, "Error", JOptionPane.ERROR_MESSAGE)
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

  def initServices(): Unit = {
    BackendManager += (new HttpBackend, 0)
  }

  def initUi(
    display:         Display,
    resources:       Resources,
    cfgMgr:          ConfigManager,
    downloadListMgr: DownloadListManager
  ): Shell = {
    new Shell(display).withCode { shell =>
      (new MainFrame(shell, resources, cfgMgr, downloadListMgr)).init()
    }
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
