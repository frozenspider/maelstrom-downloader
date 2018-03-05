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
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._

class AddDownloadFrame(
  shell:           Shell,
  resources:       Resources,
  cfgMgr:          ConfigManager,
  backendMgr:      BackendManager,
  downloadListMgr: DownloadListManager
) {
  init()

  var uriInput: Text = _
  var locationInput: Text = _
  var commentInput: Text = _

  def init(): Unit = {
    shell.setText("Add Download")

    shell.setLayout(new GridLayout())

    new Label(shell, SWT.NONE).withCode { label =>
      label.setText("URI:")
    }

    uriInput = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL).withCode { input =>
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

    new Label(shell, SWT.NONE).withCode { label =>
      label.setText("Location:")
    }

    val locationRow = new Composite(shell, SWT.NONE).withCode { row =>
      row.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false))
      row.setLayout(new GridLayout().withCode { layout =>
        layout.horizontalSpacing = 0
        layout.verticalSpacing = 0
        layout.marginWidth = 0
        layout.marginHeight = 0
        layout.numColumns = 2
      })
    }

    locationInput = new Text(locationRow, SWT.SINGLE | SWT.BORDER).withCode { input =>
      input.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, true))
      input.setText(cfgMgr.getProperty(ConfigOptions.DownloadPath))
      installDefaultHotkeys(input)
    }

    new Button(locationRow, SWT.NONE).withCode { btn =>
      btn.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false).withCode { d =>
        d.heightHint = 24
        d.widthHint = 24
      })
      btn.setImage(resources.browseIcon)
      btn.setToolTipText("Browse...")
      btn.addListener(SWT.Selection, e => browse())
    }

    new Label(shell, SWT.NONE).withCode { label =>
      label.setText("Comment:")
    }

    commentInput = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL).withCode { input =>
      input.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL).withCode { d =>
        d.heightHint = 50
        d.widthHint = 500
      })
      installDefaultHotkeys(input)
    }

    val bottomButtonRow = new Composite(shell, SWT.NONE).withCode { composite =>
      composite.setLayout(new RowLayout().withCode { layout =>
        layout.marginTop = 0
        layout.marginBottom = 0
      })
      composite.setLayoutData(new GridData(GridData.CENTER, GridData.FILL, true, true))
    }

    val okButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&OK")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => okClicked(shell))
      shell.setDefaultButton(btn)
    }

    val cancelButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&Cancel")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => shell.dispose())
    }

    shell.pack()
    centerOnScreen(shell)

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

  private def browse(): Unit = {
    val dialog = new DirectoryDialog(shell)
    dialog.setFilterPath(locationInput.getText)
    val result = dialog.open()
    Option(result) foreach locationInput.setText
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
