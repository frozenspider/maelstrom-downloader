package org.fs.mael.ui

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.MalformedURLException
import java.net.URI
import java.net.URL

import org.eclipse.swt._
import org.eclipse.swt.events._
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.UserFriendlyException
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.utils.SwtUtils._

class AddDownloadFrame(
  dialog:          Shell,
  cfgMgr:          ConfigManager,
  backendMgr:      BackendManager,
  downloadListMgr: DownloadListManager
) {
  init()

  var uriInput: Text = _
  var locationInput: Text = _
  var commentInput: Text = _

  def init(): Unit = {
    dialog.setText("Add Download")

    dialog.setLayout(new GridLayout())

    new Label(dialog, SWT.NONE).withCode { label =>
      label.setText("URI:")
    }

    uriInput = new Text(dialog, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL).withCode { input =>
      input.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL).withCode { d =>
        d.heightHint = 50
        d.widthHint = 500
      })
      input.addVerifyListener(e => {
        // Remove all line breaks
        e.text = e.text.replaceAll("[\r\n]+", "")
      })
      installDefaultHotkeys(input)
    }

    new Label(dialog, SWT.NONE).withCode { label =>
      label.setText("Location:")
    }

    // TODO: Browse button
    locationInput = new Text(dialog, SWT.SINGLE | SWT.BORDER).withCode { input =>
      input.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL))
      input.setText(cfgMgr.getProperty(ConfigOptions.DownloadPath))
      installDefaultHotkeys(input)
    }

    new Label(dialog, SWT.NONE).withCode { label =>
      label.setText("Comment:")
    }

    commentInput = new Text(dialog, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL).withCode { input =>
      input.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL).withCode { d =>
        d.heightHint = 50
        d.widthHint = 500
      })
      installDefaultHotkeys(input)
    }

    val bottomButtonRow = new Composite(dialog, SWT.NONE).withCode { composite =>
      composite.setLayout(new RowLayout().withCode { layout =>
        layout.marginTop = 0
        layout.marginBottom = 0
      })
      composite.setLayoutData(new GridData(GridData.CENTER, GridData.FILL, true, true))
    }

    val okButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&OK")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => okClicked(dialog))
      dialog.setDefaultButton(btn)
    }

    val cancelButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&Cancel")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => dialog.dispose())
    }

    dialog.pack()
    centerOnScreen(dialog)

    // Try to paste URL from clipboard
    try {
      val clipboard = Toolkit.getDefaultToolkit.getSystemClipboard
      val content = clipboard.getData(DataFlavor.stringFlavor).asInstanceOf[String].trim
      if (!content.contains("\n")) {
        val url = new URL(content)
        uriInput.setText(url.toString)
      }
    } catch {
      case ex: Exception => // Ignore
    }

    uriInput.setFocus()
  }

  private def okClicked(dialog: Shell): Unit = {
    try {
      val uriString = uriInput.getText.trim
      val locationString = locationInput.getText.trim
      val location = new File(locationString)
      val comment = commentInput.getText.trim
      val uri = new URI(uriString)
      val backendOption = backendMgr.findFor(uri)
      backendOption match {
        case Some(backend) =>
          val entry = backend.create(uri, location, None, comment)
          downloadListMgr.add(entry)
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
