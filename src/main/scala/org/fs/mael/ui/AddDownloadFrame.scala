package org.fs.mael.ui

import java.net.MalformedURLException
import java.net.URL

import org.eclipse.swt._
import org.eclipse.swt.events._
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.BackendManager
import org.fs.mael.core.CoreUtils._
import org.fs.mael.core.UserFriendlyException
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.ui.utils.SwtUtils._

class AddDownloadFrame(dialog: Shell) {
  init()

  var uriInput: Text = _

  def init(): Unit = {
    dialog.setText("Add Download")

    dialog.setLayout(new GridLayout())

    val label = new Label(dialog, SWT.NONE)
    label.setText("URI:")

    uriInput = new Text(dialog, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL)
    uriInput.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL).withChanges { d =>
      d.heightHint = 50
      d.widthHint = 500
    })
    uriInput.addVerifyListener(e => {
      // Remove all line breaks
      e.text = e.text.replaceAll("[\r\n]+", "")
    })
    installDefaultHotkeys(uriInput)

    val bottomButtonRow = new Composite(dialog, SWT.NONE)
    bottomButtonRow.setLayout(new RowLayout().withChanges { layout =>
      layout.marginTop = 0
      layout.marginBottom = 0
    })
    bottomButtonRow.setLayoutData(new GridData(GridData.CENTER, GridData.FILL, true, true))

    val okButton = new Button(bottomButtonRow, SWT.PUSH)
    okButton.setText("&OK")
    okButton.setLayoutData(new RowData(100, SWT.DEFAULT))
    okButton.addListener(SWT.Selection, e => okClicked(dialog))

    val cancelButton = new Button(bottomButtonRow, SWT.PUSH)
    cancelButton.setText("&Cancel")
    cancelButton.setLayoutData(new RowData(100, SWT.DEFAULT))
    cancelButton.addListener(SWT.Selection, e => dialog.dispose())

    dialog.pack()
    centerOnScreen(dialog)
  }

  private def okClicked(dialog: Shell): Unit = {
    try {
      val uriString = uriInput.getText.trim
      val url = new URL(uriString)
      url.getProtocol match {
        case "http" | "https" =>
          val backend = BackendManager.findFor(url.toURI).get
          val entry = backend.create(url.toURI)
          DownloadListManager.add(entry)
          dialog.dispose()
        case other => throw new UserFriendlyException(s"Unsupported scheme: $other")
      }
    } catch {
      case ex: UserFriendlyException =>
        showError(dialog, message = ex.getMessage)
      case ex: MalformedURLException =>
        showError(dialog, message = "Malformed URL")
    }
  }
}
