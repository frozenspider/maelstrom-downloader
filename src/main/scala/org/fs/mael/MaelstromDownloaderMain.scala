package org.fs.mael

import java.net.URI

import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Shell
import org.fs.mael.backend.StubBackend
import org.fs.mael.backend.http.HttpBackend
import org.fs.mael.core.BackendManager
import org.fs.mael.core.CoreUtils._
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.ui.MainFrame
import org.fs.utility.StopWatch
import org.slf4s.Logging

import com.github.nscala_time.time.Imports._

object MaelstromDownloaderMain extends App with Logging {

  //
  // Init code
  //

  log.info(BuildInfo.fullPrettyName)
  log.info("Application started, initializing...")

  val shell = StopWatch.measureAndCall {
    initServices()
    addTestData()
    launchUi()
  }((_, ms) =>
    log.info(s"Init done in ${ms} ms"))

  uiLoop()

  //
  // Methods
  //

  def initServices(): Unit = {
    BackendManager += (new HttpBackend, 0)
    BackendManager += (new StubBackend, Int.MinValue)
  }

  def addTestData(): Unit = {
    def add(uriString: String)(code: (DownloadEntry => Unit) = (de => ())): Unit = {
      val uri = new URI(uriString)
      val backend = BackendManager.findFor(uri).get
      DownloadListManager.add(backend.create(uri).withChanges(code))
    }
    add("http://www.example.com") { de =>
      de.comment = "info on example"
    }
    add("https://www.google.com") { de =>
      de.comment = "info on google"
      de.addDownloadLogEntry(LogEntry(LogEntry.Info, DateTime.parse("2000-01-01T00:00:00"), "Started"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Request, DateTime.parse("2000-01-02T00:12:34"), "Hey, you!"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Response, DateTime.parse("2000-01-03T01:23:45"), "What?"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Request, DateTime.parse("2000-01-03T01:23:45"), "Take\nthat\nmulti\nline!"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Error, DateTime.now(), "The end"))
    }
    add("https://www.facebook.com/test")()
    add("https://stackoverflow.com/questions/48859244/javafx-turn-off-font-smoothing")()
  }

  def launchUi(): Shell = {
    // https://www.eclipse.org/swt/snippets/
    val display = new Display()
    new Shell(display).withChanges { shell =>
      (new MainFrame(shell)).init()
      shell.open()
    }
  }

  def uiLoop(): Unit = {
    val display = shell.getDisplay
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) display.sleep()
    }
    display.dispose()
    log.info("Application closed")
  }
}
