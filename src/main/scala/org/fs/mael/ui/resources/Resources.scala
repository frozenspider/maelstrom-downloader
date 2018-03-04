package org.fs.mael.ui.resources

import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Display
import org.fs.mael.core.Status
import org.fs.mael.core.entry.LogEntry
import org.joda.time.format.DateTimeFormatter

trait Resources {
  def dateTimeFmt: DateTimeFormatter

  def dateFmt: DateTimeFormatter

  def timeFmt: DateTimeFormatter

  def logColor(tpe: LogEntry.Type, display: Display): Color

  def mainIcon: Image

  def icon(status: Status): Image

  def icon(logType: LogEntry.Type): Image
}
