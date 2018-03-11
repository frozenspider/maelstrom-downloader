package org.fs.mael.ui

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import java.net.URL

import org.eclipse.jface.dialogs.ProgressMonitorDialog
import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.swt._
import org.eclipse.swt.events._
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.Status
import org.fs.mael.core.backend.Backend
import org.fs.mael.core.backend.BackendConfigUi
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.checksum.ChecksumType
import org.fs.mael.core.checksum.Checksums
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.prefs.GlobalPreferences
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

class EditDownloadDialog(
  deOption:        Option[DownloadEntryView],
  parent:          Shell,
  resources:       Resources,
  globalCfgMgr:    ConfigManager,
  backendMgr:      BackendManager,
  downloadListMgr: DownloadListManager,
  eventMgr:        EventManager
) extends Logging {

  private var tabFolder: TabFolder = _

  private var uriInput: Text = _
  private var locationInput: DirectoryFieldEditor = _
  private var filenameInput: Text = _
  private var commentInput: Text = _
  private var checksumDropdown: Combo = _
  private var checksumInput: Text = _

  /** Switch to advanced mode, enabling backend-specific config options */
  private var goAdvanced: () => Unit = _
  private var backendOption: Option[Backend] = deOption map (de => backendMgr(de.backendId))
  private var backendCfgUiOption: Option[BackendConfigUi] = None

  val peer = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL).withCode { shell =>
    if (!deOption.isDefined) {
      shell.setText("Add Download")
    } else {
      shell.setText("Edit Download")
    }

    shell.setLayout(new GridLayout())
    tabFolder = new TabFolder(shell, SWT.NONE)
    tabFolder.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true))

    val mainTab = new TabItem(tabFolder, SWT.NONE).withCode { tab =>
      tab.setText("Main")
      val mainPage = new Composite(tabFolder, SWT.NONE).withCode { composite =>
        fillMainPage(composite)
      }
      tab.setControl(mainPage)
    }

    fillButtons(shell)

    shell.pack()
    centerOnScreen(shell)

    deOption foreach (de => goAdvanced())

    uriInput.setFocus()
  }

  private def fillMainPage(parent: Composite): Unit = {
    parent.setLayout(new GridLayout())

    new Label(parent, SWT.NONE).withCode { label =>
      label.setText("URI:")
    }

    // TODO: Would be nice to have word-breaking line wrap here, but it's highly non-trivial to implement
    uriInput = new Text(parent, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP).withCode { input =>
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

    new Label(parent, SWT.NONE).withCode { label =>
      label.setText("Location:")
    }

    val locationRow = new Composite(parent, SWT.NONE).withCode { row =>
      row.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false))
      row.setLayout(new GridLayout().withCode { layout =>
        layout.marginWidth = 0
        layout.marginHeight = 0
        layout.numColumns = 2
      })
    }

    locationInput = new DirectoryFieldEditor("", "", locationRow).withCode { editor =>
      editor.getLabelControl(locationRow).dispose()
      editor.setStringValue(globalCfgMgr(GlobalPreferences.DownloadPath))
      editor.setEmptyStringAllowed(false)
    }

    filenameInput = new Text(parent, SWT.SINGLE | SWT.BORDER).withCode { input =>
      input.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false))
      input.setMessage("<Filename, leave blank to deduce automatically>")
      input.addVerifyListener(e => {
        // Remove all illegal characters
        e.text = asValidFilename(e.text)
      })
      input.setToolTipText("")
      installDefaultHotkeys(input)
    }

    new Label(parent, SWT.NONE).withCode { label =>
      label.setText("Checksum:")
    }

    val checksumRow = new Composite(parent, SWT.NONE).withCode { row =>
      row.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false))
      row.setLayout(new GridLayout().withCode { layout =>
        layout.marginWidth = 0
        layout.marginHeight = 0
        layout.numColumns = 2
      })
    }

    checksumDropdown = new Combo(checksumRow, SWT.READ_ONLY).withCode { dropdown =>
      dropdown.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))
      dropdown.setItems(ChecksumType.values().map(_.name): _*)
    }

    checksumInput = new Text(checksumRow, SWT.SINGLE | SWT.BORDER).withCode { input =>
      input.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false).withCode { d =>
        d.widthHint = 520
      })
      input.setFont(new Font(parent.getDisplay, monospacedFontData))
      input.addVerifyListener(e => {
        if (!e.text.isEmpty && !e.text.matches(Checksums.HexRegex)) {
          e.doit = false
        }
      })
      installDefaultHotkeys(input)
    }

    new Label(parent, SWT.NONE).withCode { label =>
      label.setText("Comment:")
    }

    commentInput = new Text(parent, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP).withCode { input =>
      input.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false).withCode { d =>
        d.heightHint = 100
        d.widthHint = 500
      })
      installDefaultHotkeys(input)
    }

    deOption match {
      case Some(de) =>
        uriInput.setText(de.uri.toString)
        uriInput.setEditable(de.status.canBeStarted)

        locationInput.setStringValue(de.location.getAbsolutePath)
        // Changing enabled state in a tricky way so that path can still be selected
        val enabled = de.status != Status.Running
        locationInput.setEnabled(enabled, locationRow)
        locationInput.getTextControl(locationRow).withCode { input =>
          input.setEnabled(true)
          input.setEditable(enabled)
        }

        filenameInput.setText(de.filenameOption getOrElse "")

        checksumDropdown.setEnabled(de.status != Status.Complete)
        checksumInput.setEditable(de.status != Status.Complete)

        de.checksumOption match {
          case Some(Checksum(tpe, value)) =>
            checksumDropdown.select(tpe.ordinal)
            checksumInput.setText(value)
          case None =>
          // NOOP
        }

        commentInput.setText(de.comment)

      case None =>
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
    }
  }

  private def fillButtons(shell: Shell): Unit = {
    val bottomButtonRow = new Composite(shell, SWT.NONE).withCode { composite =>
      composite.setLayout(new RowLayout().withCode { layout =>
        layout.marginTop = 0
        layout.marginBottom = 0
      })
      composite.setLayoutData(new GridData(GridData.CENTER, GridData.CENTER, true, false))
    }

    val advancedButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&Advanced")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
    }

    val okButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&OK")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => okClicked())
      shell.setDefaultButton(btn)
    }

    val cancelButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&Cancel")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => shell.dispose())
    }

    goAdvanced = () => advancedClicked(advancedButton, okButton, cancelButton)
    advancedButton.addListener(SWT.Selection, e => goAdvanced())
  }

  private def getUri(): URI = {
    new URI(uriInput.getText.trim)
  }

  private def getBackend(uri: URI): Backend = {
    backendOption getOrElse {
      backendMgr findFor (uri) getOrElse {
        failFriendly("Malformed or unsupported URI scheme")
      }
    }
  }

  private def advancedClicked(advancedButton: Button, okButton: Button, cancelButton: Button): Unit = {
    tryShowingError(peer, log) {
      val backend = backendOption getOrElse getBackend(getUri())
      // From now on, backend is frozen
      backendOption = Some(backend)
      val deCfgOption = deOption map (_.backendSpecificCfg)
      backendCfgUiOption = Some(backend.layoutConfig(deCfgOption, tabFolder))

      // Re-do buttons layout, hiding "advanced" button
      advancedButton.dispose()
      okButton.getLayoutData.asInstanceOf[RowData].width = 150
      cancelButton.getLayoutData.asInstanceOf[RowData].width = 150
      okButton.getParent.pack()
    }
  }

  private def okClicked(): Unit = {
    tryShowingError(peer, log) {
      requireFriendly(locationInput.isValid, "Invalid location: " + locationInput.getErrorMessage)
      val locationString = locationInput.getStringValue.trim
      val location = new File(locationString)
      val filenameOption = filenameInput.getText.trim match {
        case "" => None
        case s  => Some(s)
      }
      val comment = commentInput.getText.trim
      val uri = getUri()
      val backend = getBackend(uri)
      requireFriendly(backend isSupported uri, "Incompatible URI scheme")
      val checksumString = checksumInput.getText
      val checksumOption = if (!checksumString.isEmpty) {
        val tpe = if (checksumDropdown.getSelectionIndex == -1) {
          Checksums.guessType(checksumString) getOrElse {
            failFriendly("Please select checksum type")
          }
        } else {
          ChecksumType.values()(checksumDropdown.getSelectionIndex)
        }
        requireFriendly(Checksums.isProper(tpe, checksumString), "Malformed checksum")
        Some(Checksum(tpe, checksumString.toLowerCase))
      } else {
        None
      }
      val deCfgOption = backendCfgUiOption map (_.get())
      deOption match {
        case None     => create(backend, uri, location, filenameOption, checksumOption, comment)(deCfgOption)
        case Some(de) => edit(de, backend, uri, location, filenameOption, checksumOption, comment)(deCfgOption)
      }
      peer.dispose()
    }
  }

  private def create(
    backend:        Backend,
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String
  )(deCfgOption: Option[InMemoryConfigManager]): Unit = {
    val entry = backend.create(uri, location, filenameOption, checksumOption, comment, deCfgOption)
    downloadListMgr.add(entry)
  }

  private def edit(
    dev:            DownloadEntryView,
    backend:        Backend,
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String
  )(deCfgOption: Option[InMemoryConfigManager]): Unit = {
    val de = dev.asInstanceOf[DownloadEntry]
    val newFilenameOption = filenameOption orElse de.filenameOption
    if (location != de.location || filenameOption != de.filenameOption) {
      if (de.downloadedSize > 0) {
        val Some(filename) = newFilenameOption
        val oldFile = de.fileOption.get
        if (oldFile.exists) {
          relocateWithProgress(oldFile, new File(location, filename))
        }
      }
      de.location = location
      de.filenameOption = newFilenameOption
    }
    de.uri = uri
    de.checksumOption = checksumOption
    de.comment = comment
    deCfgOption foreach { cfg =>
      de.backendSpecificCfg.resetTo(cfg)
    }
    downloadListMgr.save() // We don't want to lose changes
    eventMgr.fireDetailsChanged(de)
    eventMgr.fireConfigChanged(de)
  }

  private def relocateWithProgress(from: File, to: File): Unit = {
    requireFriendly(!to.exists, "File with the same name already exists in the specified location")
    (new ProgressMonitorDialog(peer)).run(true, true, monitor => {
      monitor.beginTask("Moving file", (from.length / 100).toInt)
      try {
        moveFile(from.toPath, to.toPath, (portion, total) => {
          if (monitor.isCanceled) throw new InterruptedException
          monitor.worked((portion / 100).toInt)
        })
      } finally {
        monitor.done()
      }
    })
  }
}
