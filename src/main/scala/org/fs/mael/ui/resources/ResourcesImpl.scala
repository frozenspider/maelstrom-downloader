package org.fs.mael.ui.resources

import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.ImageData
import org.eclipse.swt.widgets.Display
import org.fs.mael.core.Status
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.utils.CoreUtils._
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

class ResourcesImpl(display: Display) extends Resources {
  override def dateTimeFmt: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  override def dateFmt: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  override def timeFmt: DateTimeFormatter = DateTimeFormat.forPattern("HH:mm:ss")

  override def logColor(tpe: LogEntry.Type, display: Display): Color = tpe match {
    case LogEntry.Info     => new Color(display, 0xE4, 0xF1, 0xFF)
    case LogEntry.Request  => new Color(display, 0xFF, 0xFF, 0xDD)
    case LogEntry.Response => new Color(display, 0xEB, 0xFD, 0xEB)
    case LogEntry.Error    => new Color(display, 0xFF, 0xDD, 0xDD)
  }

  override def mainIcon: Image = icons.main

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

  private def loadImage(name: String): Image = {
    val stream = this.getClass.getResourceAsStream("/icons/" + name)
    try {
      new Image(display, new ImageData(stream))
    } finally {
      stream.close()
    }
  }

  private def rescale(image: Image, sizes: (Int, Int) = (16, 16)): Image = {
    new Image(display, image.getImageData.scaledTo(16, 16))
  }

  object icons {
    val main: Image = loadImage("main.ico")

    val play: Image = rescale(loadImage("play.png"))
    val stop: Image = rescale(loadImage("stop.png"))
    val error: Image = rescale(loadImage("error.png"))
    val check: Image = rescale(loadImage("check.png"))

    val info: Image = rescale(loadImage("info.png"))
    val request: Image = rescale(loadImage("request.png"))
    val response: Image = rescale(loadImage("response.png"))
    val errorCircle: Image = rescale(loadImage("error-circle.png"))

    val empty: Image = {
      new Image(display, new Image(display, 1, 1).getImageData.withCode { idt =>
        idt.setAlpha(0, 0, 0)
      })
    }
  }
}
