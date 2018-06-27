package org.fs.mael.backend.http.ui

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.Promise

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.backend.http.CookiesConfigSetting
import org.fs.mael.backend.http.HttpUtils
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

class CookiesFieldEditorDialog(parent: Shell, initialCookiesMap: ListMap[String, String]) extends Logging {

  private var result: Promise[Option[ListMap[String, String]]] = Promise()
  private var editors: IndexedSeq[(Text, Text, Button)] = IndexedSeq.empty

  private val shell = new Shell(parent, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL)
  shell.setMinimumSize(550, 100)
  shell.setText("Cookies Editor")
  shell.setLayout(new GridLayout())

  // Since tooltips for top-level MenuItem doesn't work, we're using surrogate menu bar
  new Composite(shell, SWT.NONE).withCode { menu =>
    menu.setLayout((new RowLayout).withCode { layout =>
      layout.marginTop = 0
      layout.marginBottom = 0
      layout.marginRight = 0
      layout.marginLeft = 0
    })
    menu.setLayoutData(new GridData(SWT.FILL, SWT.LEAD, true, false))

    val itemAdd = new Button(menu, SWT.PUSH)
    itemAdd.setText("Add new row")
    itemAdd.addListener(SWT.Selection, e => {
      appendRow("", "")
      updateScroll()
    })

    val itemFromClipboard = new Button(menu, SWT.PUSH)
    itemFromClipboard.setText("Import from clipboard")
    itemFromClipboard.setToolTipText("""|Parse a cookie string
      |(either starting with Cookie: or Set-Cookie: or not) from clipboard and import
      |it in into the editor""".stripMargin)
    itemFromClipboard.addListener(SWT.Selection, e => interactiveImportFromClipboard(false))
  }

  private val scrollpane = new ScrolledComposite(shell, SWT.H_SCROLL | SWT.V_SCROLL)
  scrollpane.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
  scrollpane.setExpandHorizontal(true)
  scrollpane.setExpandVertical(true)
  private val dataPane = new Composite(scrollpane, SWT.NONE)
  dataPane.setLayout(new GridLayout(3, false).withCode { layout =>
    layout.horizontalSpacing = 1
    layout.verticalSpacing = 1
  })
  scrollpane.setContent(dataPane)

  render(initialCookiesMap)

  fillBottomButtons(shell)

  shell.pack()
  centerOnScreen(shell)

  interactiveImportFromClipboard(true)

  def prompt(): Future[Option[ListMap[String, String]]] = {
    require(!result.isCompleted, "Duplicate call to prompt")
    this.shell.open()
    result.future
  }

  private def render(cookiesMap: ListMap[String, String]) = {
    dataPane.getChildren foreach (_.dispose)
    editors = IndexedSeq.empty
    val keyValueSeq = cookiesMap.toSeq :+ ("", "")
    keyValueSeq foreach {
      case (k, v) => appendRow(k, v)
    }
    updateScroll()
  }

  private def appendRow(k: String, v: String): Unit = {
    val nameEditor = new Text(dataPane, SWT.BORDER)
    val valueEditor = new Text(dataPane, SWT.BORDER)
    val removeBtn = new Button(dataPane, SWT.NONE)
    val tuple = (nameEditor, valueEditor, removeBtn)

    nameEditor.setLayoutData(new GridData(SWT.FILL, SWT.LEAD, true, false))
    nameEditor.setText(k)
    nameEditor.setToolTipText("Cookie name")
    valueEditor.setLayoutData(new GridData(SWT.FILL, SWT.LEAD, true, false))
    valueEditor.setText(v)
    valueEditor.setToolTipText("Cookie value")
    removeBtn.setText("-")
    removeBtn.setToolTipText("Remove cookie entry")
    removeBtn.setFont(new Font(parent.getDisplay, monospacedFontData))
    removeBtn.setLayoutData(new GridData(SWT.TRAIL, SWT.LEAD, false, false).withCode { gd =>
      val sz = valueEditor.computeSize(SWT.DEFAULT, SWT.DEFAULT)
      gd.widthHint = sz.y
      gd.heightHint = sz.y
    })
    removeBtn.addListener(SWT.Selection, e => {
      editors = editors filter { case (_, _, btn2) => btn2 != removeBtn }
      tuple.productIterator.foreach { case w: Widget => w.dispose() }
      updateScroll()
    })
    editors = editors :+ tuple
  }

  private def updateScroll(): Unit = {
    scrollpane.setMinSize(dataPane.computeSize(SWT.DEFAULT, SWT.DEFAULT))
    dataPane.layout()
  }

  private def fillBottomButtons(shell: Shell): Unit = {
    val bottomButtonRow = new Composite(shell, SWT.NONE).withCode { composite =>
      composite.setLayout(new RowLayout().withCode { layout =>
        layout.marginTop = 0
        layout.marginBottom = 0
      })
      composite.setLayoutData(new GridData(SWT.CENTER, SWT.TRAIL, true, false))
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
      btn.addListener(SWT.Selection, e => cancelClicked())
    }
  }

  /**
   * Try to (interactively) import cookies from clipboard, showing confirmations and errors
   * @param silent if `true`, only proceed with import if no user input is needed
   */
  private def interactiveImportFromClipboard(silent: Boolean): Unit = {
    val title = "Cookies Import"
    try {
      val cookieString = getStringFromClipboard()
      val cookiesMap = HttpUtils.parseCookies(cookieString)

      if ((initialCookiesMap.isEmpty) || (
        !silent
        && MessageDialog.openConfirm(parent, title, "Do you want to replace current cookies with the clipboard content?")
      )) {
        render(cookiesMap)
      }
    } catch {
      case ex: Exception if silent => // NOOP
      case ex: Exception           => MessageDialog.openError(shell, title, "Clipboard doesn't contain a valid cookie string")
    }
  }

  private def okClicked(): Unit = {
    tryShowingError(shell, log) {
      val nameValPairs = editors.map {
        case (nameEditor, valueEditor, _) => (nameEditor.getText.trim, valueEditor.getText.trim)
      } filter {
        case (k, v) => !k.isEmpty // Value can be empty
      }

      val duplicates = nameValPairs.groupBy(_._1).collect { case (n, vs) if vs.size > 1 => (n, vs.size) }
      requireFriendly(duplicates.size == 0, "Duplicate keys: " + duplicates.keys.mkString(", "))
      nameValPairs.foreach {
        case (k, v) => CookiesConfigSetting.validateCharacterSet(k, v)
      }

      result.success(Some(ListMap(nameValPairs: _*)))
      shell.dispose()
    }
  }

  private def cancelClicked() = {
    result.success(None)
    shell.dispose()
  }
}
