package org.fs.mael.ui.resources

import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.ImageData
import org.eclipse.swt.widgets.Display
import org.fs.mael.core.CoreUtils._
import org.fs.mael.core.Status
import org.fs.mael.core.entry.LogEntry

class ResourcesImpl(display: Display) extends Resources {
  override def icon(status: Status): Image = status match {
    case Status.Running  => icons.play
    case Status.Stopped  => icons.stop
    case Status.Error    => icons.error
    case Status.Complete => icons.check
  }

  override def icon(logType: LogEntry.Type): Image = logType match {
    case LogEntry.Info     => icons.info
    case LogEntry.Request  => icons.request
    case LogEntry.Response => icons.response
    case LogEntry.Error    => icons.errorCircle
  }

  private def loadIcon(name: String): Image = {
    val stream = this.getClass.getResourceAsStream("/icons/" + name)
    try {
      val loaded = new ImageData(stream)
      new Image(display, loaded.scaledTo(16, 16))
    } finally {
      stream.close()
    }
  }

  object icons {
    val play: Image = loadIcon("play.png")
    val stop: Image = loadIcon("stop.png")
    val error: Image = loadIcon("error.png")
    val check: Image = loadIcon("check.png")

    val info: Image = loadIcon("info.png")
    val request: Image = loadIcon("request.png")
    val response: Image = loadIcon("response.png")
    val errorCircle: Image = loadIcon("error-circle.png")

    val empty: Image = {
      new Image(display, new Image(display, 1, 1).getImageData.withCode { idt =>
        idt.setAlpha(0, 0, 0)
      })
    }
  }
}
