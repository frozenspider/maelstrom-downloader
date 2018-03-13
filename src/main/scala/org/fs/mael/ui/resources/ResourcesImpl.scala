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
  override lazy val dateTimeFmt: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  override lazy val dateFmt: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  override lazy val timeFmt: DateTimeFormatter = DateTimeFormat.forPattern("HH:mm:ss")

  override def logColor(tpe: LogEntry.Type, display: Display): Color = tpe match {
    case LogEntry.Info     => new Color(display, 0xE4, 0xF1, 0xFF)
    case LogEntry.Request  => new Color(display, 0xFF, 0xFF, 0xDD)
    case LogEntry.Response => new Color(display, 0xEB, 0xFD, 0xEB)
    case LogEntry.Error    => new Color(display, 0xFF, 0xDD, 0xDD)
  }

  override lazy val mainIcon: Image = icons.main

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

  private def loadImageData(name: String): ImageData = {
    val stream = this.getClass.getResourceAsStream("/icons/" + name)
    val imageData = try {
      new ImageData(stream)
    } finally {
      stream.close()
    }
    imageData
  }

  object icons {
    lazy val main: Image = loadImageData("main.ico").toImage()

    lazy val play: Image = loadImageData("play.png").scaledTo(16, 16).toImage()
    lazy val stop: Image = loadImageData("stop.png").scaledTo(16, 16).toImage()
    lazy val error: Image = loadImageData("error.png").scaledTo(16, 16).toImage()
    lazy val check: Image = loadImageData("check.png").scaledTo(16, 16).toImage()

    lazy val info: Image = loadImageData("info.png").scaledTo(16, 16).toImage()
    lazy val request: Image = loadImageData("request.png").scaledTo(16, 16).toImage()
    lazy val response: Image = loadImageData("response.png").scaledTo(16, 16).toImage()
    lazy val errorCircle: Image = loadImageData("error-circle.png").scaledTo(16, 16).toImage()

    lazy val empty: Image = {
      new Image(display, new Image(display, 1, 1).getImageData.withCode { idt =>
        idt.setAlpha(0, 0, 0)
      })
    }
  }

  private implicit class ImageDataExt(id: ImageData) {
    def toImage(): Image = new Image(display, id)

    def withStroke(alpha: Int = 0x50): ImageData = {
      def getAlpha(x: Int, y: Int): Int = {
        if (x < 0 || x >= id.width || y < 0 || y >= id.height) {
          0x00
        } else id.getAlpha(x, y)
      }
      def isOuterBorder(x: Int, y: Int): Boolean = {
        getAlpha(x, y) == 0x00 && (
          getAlpha(x - 1, y + 0) > 0x00 ||
          getAlpha(x + 1, y + 0) > 0x00 ||
          getAlpha(x + 0, y - 1) > 0x00 ||
          getAlpha(x + 0, y + 1) > 0x00 ||
          getAlpha(x - 1, y - 1) > 0x00 ||
          getAlpha(x + 1, y + 1) > 0x00 ||
          getAlpha(x + 1, y - 1) > 0x00 ||
          getAlpha(x - 1, y + 1) > 0x00 ||
          false
        )
      }
      val copy = id.clone().asInstanceOf[ImageData]
      for {
        x <- 0 until copy.width
        y <- 0 until copy.height
        if isOuterBorder(x, y)
      } {
        copy.setAlpha(x, y, alpha)
        copy.setPixel(x, y, 0x000000)
      }
      copy
    }
  }
}
