package org.fs.mael

import java.net.URI

import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Shell
import org.fs.mael.backend.StubBackend
import org.fs.mael.core.BackendManager
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.ui.MainFrame
import org.fs.mael.ui.helper.SwtHelper._

import com.github.nscala_time.time.Imports._

object MaelstromEntry extends App {

  initServices()
  addTestData()
  launchUi()

  def initServices(): Unit = {
    BackendManager += (new StubBackend)
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

  def launchUi(): Unit = {
    // https://www.eclipse.org/swt/snippets/
    val display = new Display()
    val shell = new Shell(display)

    (new MainFrame(shell)).init()

    shell.setSize(1000, 600)
    centerOnScreen(shell)
    shell.open()

    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) display.sleep()
    }
    display.dispose()
  }
}
