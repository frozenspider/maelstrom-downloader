package org.fs.mael.ui

import java.io.File
import java.net.URI
import java.net.URL

import scala.util.Try

import org.eclipse.jface.dialogs.ProgressMonitorDialog
import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.swt._
import org.eclipse.swt.events._
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.backend.http.HttpBackend
import org.fs.mael.core.Status
import org.fs.mael.core.backend.Backend
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.backend.ui.BackendConfigUi
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.checksum.ChecksumType
import org.fs.mael.core.checksum.Checksums
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.list.DownloadListManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.config.GlobalSettings
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

class EditDownloadDialog(
  _deOption:       Option[DownloadEntry],
  parent:          Shell,
  resources:       Resources,
  globalCfg:       IGlobalConfigStore,
  backendMgr:      BackendManager,
  downloadListMgr: DownloadListManager,
  eventMgr:        EventManager
) extends Logging {

  private val isNewDownload = !_deOption.isDefined
  private var tabFolder: TabFolder = _

  private var uriInput: Text = _
  private var locationRow: Composite = _ // Serves as a parent for locationInput
  private var locationInput: DirectoryFieldEditor = _
  private var filenameInput: Text = _
  private var commentInput: Text = _
  private var checksumDropdown: Combo = _
  private var checksumInput: Text = _
  private var autostartCheckbox: Button = _

  /** Switch to advanced mode, enabling backend-specific config options */
  private var goAdvanced: () => Unit = _
  private var backendOption: Option[Backend] = _
  private var backendCfgUiOption: Option[BackendConfigUi] = None

  val shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL).withCode { shell =>
    isNewDownload match {
      case true  => shell.setText("Add Download")
      case false => shell.setText("Edit Download")
    }

    shell.setLayout(new GridLayout())
  }

  tabFolder = new TabFolder(shell, SWT.NONE)
  tabFolder.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true))

  val mainTab = new TabItem(tabFolder, SWT.NONE).withCode { tab =>
    tab.setText("Main")
    val mainPage = new Composite(tabFolder, SWT.NONE)
    fillMainPage(mainPage)
    tab.setControl(mainPage)
  }

  fillButtons(shell)

  shell.pack()
  centerOnScreen(shell)

  private val deOption = _deOption orElse initWithClipboard()
  backendOption = deOption map (de => backendMgr(de.backendId))

  deOption foreach (de => {
    uriInput.setText(de.uri.toString)
    uriInput.setEditable(de.status.canBeStarted)

    locationInput.setStringValue(de.location.getAbsolutePath)
    filenameInput.setText(de.filenameOption getOrElse "")
    if (de.status == Status.Running) {
      disable(locationInput, locationRow)
      filenameInput.setEditable(false)
    }

    checksumDropdown.setEnabled(de.status != Status.Complete)
    checksumInput.setEditable(de.status != Status.Complete)

    de.checksumOption match {
      case Some(Checksum(tpe, value)) =>
        checksumDropdown.select(tpe.ordinal)
        checksumInput.setText(value)
      case None => // NOOP
    }

    commentInput.setText(de.comment)

    goAdvanced()
  })
  uriInput.setFocus()

  /** Try to initialize download entry - or at least URI field - from clipboard */
  private def initWithClipboard(): Option[DownloadEntry] = {
    Try {
      val content = Clipboard.getString()
      Try {
        require(!content.contains("\n"))
        val url = new URL(content)
        uriInput.setText(url.toString)
        None
      } orElse {
        val httpBackend = backendMgr(HttpBackend.Id).asInstanceOf[HttpBackend]
        val location = new File(locationInput.getStringValue.trim)
        Try {
          Some(httpBackend.parseCurlRequest(content, location))
        } orElse Try {
          Some(httpBackend.parsePlaintextRequest(content, location))
        }
      }
    }.flatten.toOption.flatten
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

    locationRow = new Composite(parent, SWT.NONE).withCode { row =>
      row.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false))
      row.setLayout(new GridLayout().withCode { layout =>
        layout.marginWidth = 0
        layout.marginHeight = 0
        layout.numColumns = 2
      })
    }

    locationInput = new DirectoryFieldEditor("", "", locationRow).withCode { editor =>
      editor.getLabelControl(locationRow).dispose()
      editor.setStringValue(globalCfg(GlobalSettings.DownloadPath))
      editor.setEmptyStringAllowed(false)
    }

    filenameInput = new Text(parent, SWT.SINGLE | SWT.BORDER).withCode { input =>
      input.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false))
      input.setMessage("<Filename, leave blank to deduce automatically>")
      input.addVerifyListener(e => {
        // Remove all illegal characters
        e.text = asValidFilename(e.text)
      })
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

    autostartCheckbox = new Button(parent, SWT.CHECK).withCode { checkbox =>
      checkbox.setText("Start download right away")
      if (isNewDownload) {
        checkbox.setSelection(globalCfg(GlobalSettings.AutoStartDownloads))
      } else {
        checkbox.setSelection(false)
        checkbox.setEnabled(false)
      }
    }
  }

  private def fillButtons(shell: Shell): Unit = {
    val bottomButtonRow = new Composite(shell, SWT.NONE).withCode { composite =>
      composite.setLayout(new RowLayout().withCode { layout =>
        layout.marginTop = 0
        layout.marginBottom = 0
      })
      composite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false))
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
    tryShowingError(shell, log) {
      val backend = backendOption getOrElse getBackend(getUri())
      // From now on, backend is frozen
      backendOption = Some(backend)
      val deCfgOption = deOption map (_.backendSpecificCfg)
      val isEditable = deOption map (de => de.status.canBeStarted) getOrElse true
      backendCfgUiOption = Some(backend.layoutConfig(deCfgOption, tabFolder, isEditable))

      // Re-do buttons layout, hiding "advanced" button
      advancedButton.dispose()
      okButton.getLayoutData.asInstanceOf[RowData].width = 150
      cancelButton.getLayoutData.asInstanceOf[RowData].width = 150
      okButton.getParent.pack()
    }
  }

  private def okClicked(): Unit = {
    tryShowingError(shell, log) {
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
      shell.dispose()
    }
  }

  private def create(
    backend:        Backend,
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String
  )(deCfgOption: Option[BackendConfigStore]): Unit = {
    val de = backend.create(uri, location, filenameOption, checksumOption, comment, deCfgOption)
    downloadListMgr.add(de)
    processAutostart(backend, de)
  }

  private def edit(
    de:             DownloadEntry,
    backend:        Backend,
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String
  )(deCfgOption: Option[BackendConfigStore]): Unit = {
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
    downloadListMgr.add(de) // This is needed if download entry was imported from clipboard
    downloadListMgr.save() // We don't want to lose changes
    eventMgr.fireDetailsChanged(de)
    eventMgr.fireConfigChanged(de)
    if (isNewDownload) {
      processAutostart(backend, de)
    }
  }

  private def processAutostart(backend: Backend, de: DownloadEntry): Unit = {
    if (autostartCheckbox.getSelection) {
      backend.downloader.start(de, globalCfg(GlobalSettings.ConnectionTimeout))
    }
    globalCfg.set(GlobalSettings.AutoStartDownloads, autostartCheckbox.getSelection)
  }

  private def relocateWithProgress(from: File, to: File): Unit = {
    requireFriendly(!to.exists, "File with the same name already exists in the specified location")
    (new ProgressMonitorDialog(shell)).run(true, true, monitor => {
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
