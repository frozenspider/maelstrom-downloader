package org.fs.mael.ui

import org.eclipse.swt.widgets.Shell

trait DialogController {
  def showDialog(parent: Shell): Unit
}
