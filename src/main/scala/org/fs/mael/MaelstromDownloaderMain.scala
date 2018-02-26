package org.fs.mael

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Shell
import org.fs.mael.backend.StubBackend
import org.fs.mael.backend.http.HttpBackend
import org.fs.mael.core.BackendManager
import org.fs.mael.core.CoreUtils._
import org.fs.mael.core.Status
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.ui.MainFrame
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.resources.ResourcesImpl
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
    val display = new Display()
    // TODO: Show minimal splash screen
    val resources = new ResourcesImpl(display)
    preloadClasses()
    initServices()
    addTestData()
    initUi(display, resources)
  }((_, ms) =>
    log.info(s"Init done in ${ms} ms"))

  uiLoop()

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
    BackendManager += (new StubBackend, Int.MinValue)
  }

  def addTestData(): Unit = {
    var entries: Seq[DownloadEntry] = Seq.empty

    def add(uriString: String)(code: (DownloadEntry => Unit) = (de => ())): Unit = {
      val loc = new File(System.getProperty("java.io.tmpdir"))
      val uri = new URI(uriString)
      val backend = BackendManager.findFor(uri).get
      entries = entries :+ backend.create(uri, loc).withCode(code)
    }
    add("http://www.example.com") { de =>
      de.comment = "info on example"
      de.status = Status.Error
    }
    add("https://www.google.com") { de =>
      de.comment = "info on google"
      de.addDownloadLogEntry(LogEntry(LogEntry.Info, DateTime.parse("2000-01-01T00:00:00"), "Started"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Request, DateTime.parse("2000-01-02T00:12:34"), "Hey, you!"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Response, DateTime.parse("2000-01-03T01:23:45"), "What?"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Request, DateTime.parse("2000-01-03T01:23:45"), "Take\nthat\nmulti\nline!"))
      de.addDownloadLogEntry(LogEntry(LogEntry.Error, DateTime.now(), "The end"))
      de.status = Status.Running
    }
    add("https://www.facebook.com/test") { de =>
      de.supportsResumingOption = Some(false)
    }
    add("https://stackoverflow.com/questions/48859244/javafx-turn-off-font-smoothing") { de =>
      de.status = Status.Complete
    }
    add("http://ipv4.download.thinkbroadband.com/5MB.zip") { de =>
      de.comment = "5MB file"
    }
    add("http://ipv4.download.thinkbroadband.com/20MB.zip") { de =>
      de.comment = "20MB file"
    }
    add("http://www.ovh.net/files/10Mb.dat") { de =>
      de.comment = "1.25 MB file"
      //MD5 62501d556539559fb422943553cd235a
    }
    add("http://mirror.filearena.net/pub/speed/SpeedTest_16MB.dat") { de =>
      de.comment = "16MB file"
      //MD5 2c7ab85a893283e98c931e9511add182
    }
    add("https://www.blender.org/wp-content/uploads/2015/04/foryou.png?x34953") { de =>
      de.comment = "Image"
    }

    DownloadListManager.init(entries)
  }

  def initUi(display: Display, resources: Resources): Shell = {
    new Shell(display).withCode { shell =>
      (new MainFrame(shell, resources)).init()
    }
  }

  def uiLoop(): Unit = {
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
