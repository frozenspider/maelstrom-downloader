package org.fs.mael.ui.resources

import org.eclipse.swt.graphics.Image
import org.fs.mael.core.Status
import org.fs.mael.core.entry.LogEntry

trait Resources {
  def icon(status: Status): Image

  def icon(logType: LogEntry.Type): Image
}
