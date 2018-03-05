package org.fs.mael.ui

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import java.net.URL

import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.swt._
import org.eclipse.swt.events._
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.UserFriendlyException
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

class EditDownloadDialog(
  deOption:        Option[DownloadEntryView],
  parent:          Shell,
  resources:       Resources,
  cfgMgr:          ConfigManager,
  backendMgr:      BackendManager,
  downloadListMgr: DownloadListManager
) extends Logging {

  var uriInput: Text = _
  var locationInput: DirectoryFieldEditor = _
  var commentInput: Text = _

  val peer = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL).withCode { peer =>
    peer.setText("Add Download")

    peer.setLayout(new GridLayout())

    new Label(peer, SWT.NONE).withCode { label =>
      label.setText("URI:")
    }

    uriInput = new Text(peer, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL).withCode { input =>
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

    new Label(peer, SWT.NONE).withCode { label =>
      label.setText("Location:")
    }

    val locationRow = new Composite(peer, SWT.NONE).withCode { row =>
      row.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false))
      row.setLayout(new GridLayout().withCode { layout =>
        layout.horizontalSpacing = 0
        layout.verticalSpacing = 0
        layout.marginWidth = 0
        layout.marginHeight = 0
        layout.numColumns = 2
      })
    }

    locationInput = new DirectoryFieldEditor("", "", locationRow).withCode { editor =>
      editor.getLabelControl(locationRow).dispose()
      editor.setStringValue(cfgMgr.getProperty(ConfigOptions.DownloadPath))
      editor.setEmptyStringAllowed(false)
    }

    new Label(peer, SWT.NONE).withCode { label =>
      label.setText("Comment:")
    }

    commentInput = new Text(peer, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL).withCode { input =>
      input.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL).withCode { d =>
        d.heightHint = 50
        d.widthHint = 500
      })
      installDefaultHotkeys(input)
    }

    val bottomButtonRow = new Composite(peer, SWT.NONE).withCode { composite =>
      composite.setLayout(new RowLayout().withCode { layout =>
        layout.marginTop = 0
        layout.marginBottom = 0
      })
      composite.setLayoutData(new GridData(GridData.CENTER, GridData.FILL, true, true))
    }

    val okButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&OK")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => okClicked())
      peer.setDefaultButton(btn)
    }

    val cancelButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&Cancel")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => peer.dispose())
    }

    peer.pack()
    centerOnScreen(peer)

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

  private def okClicked(): Unit = {
    try {
      val uriString = uriInput.getText.trim
      if (!locationInput.isValid) {
        throw new UserFriendlyException("Invalid location: " + locationInput.getErrorMessage)
      }
      val locationString = locationInput.getStringValue.trim
      val location = new File(locationString)
      val comment = commentInput.getText.trim
      val uri = new URI(uriString)
      val backendOption = backendMgr.findFor(uri)
      val backend = backendOption getOrElse {
        throw new UserFriendlyException(s"Malformed or unsupported URI scheme")
      }
      val entry = backend.create(uri, location, None, comment)
      downloadListMgr.add(entry)
      peer.dispose()
    } catch {
      case ex: UserFriendlyException =>
        showError(peer, message = ex.getMessage)
      case ex: Throwable =>
        log.error("Unexpected error", ex)
        showError(peer, message = ex.toString)
    }
  }
}
