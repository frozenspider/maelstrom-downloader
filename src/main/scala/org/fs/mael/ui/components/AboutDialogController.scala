package org.fs.mael.ui.components

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.program.Program
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.widgets.Shell
import org.fs.mael.BuildInfo
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.DialogController
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils

class AboutDialogController(resources: Resources) extends DialogController {
  override def showDialog(parent: Shell): Unit = {
    val msg = s"""
      |${BuildInfo.prettyName}
      |Version ${BuildInfo.version}
      |Built with love at ${BuildInfo.builtAtString}
      |
      |Copyright 2018 by frozenspider
      |(MIT license)
    """.stripMargin.trim
    val dlg = new MessageDialog(parent, "About", null, msg, MessageDialog.INFORMATION, 0, "OK") {
      override def getInfoImage: Image = resources.mainIcon
      override def createCustomArea(parent: Composite): Control =
        new Composite(parent, SWT.NONE).withCode { parent =>
          parent.setLayout(new RowLayout(SWT.VERTICAL))
          val githubUrlString = BuildInfo.homepage.get.toString
          createLink(parent, "GitHub project page", githubUrlString)
          createLink(parent, "Report an issue or request a feature", githubUrlString + "/issues/new")
        }
    }
    dlg.open()
  }

  private def createLink(parent: Composite, desc: String, urlString: String): Link = {
    val link = new Link(parent, SWT.NONE)
    link.setText(s"""<a href="$urlString">$desc</a>""")
    link.addSelectionListener(SwtUtils.toSelectionListener(e => Program.launch(urlString)))
    link
  }
}
