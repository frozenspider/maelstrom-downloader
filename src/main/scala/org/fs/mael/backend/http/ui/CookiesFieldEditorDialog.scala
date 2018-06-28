package org.fs.mael.backend.http.ui

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets._
import org.fs.mael.backend.http.utils.HttpUtils
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.components.StringTablePopupEditorDialog
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

class CookiesFieldEditorDialog(parent: Shell, initialCookiesMap: ListMap[String, String])
  extends StringTablePopupEditorDialog("Cookies Editor", parent, initialCookiesMap)
  with Logging {

  override protected lazy val nameColumnHeader: String = "Cookie name"
  override protected lazy val valueColumnHeader: String = "Cookie value"
  override protected lazy val removeEntryTooltipText: String = "Remove cookie entry"

  override protected def init(): Unit = {
    super.init()
    interactiveImportFromClipboard(true)
  }

  override protected def fillTopButtonsInto(topButtonRow: Composite): Unit = {
    super.fillTopButtonsInto(topButtonRow)

    val itemFromClipboard = new Button(topButtonRow, SWT.PUSH)
    itemFromClipboard.setText("Import from clipboard")
    itemFromClipboard.setToolTipText("""|Parse a cookie string
      |(with leading "Cookie: " being optional) from clipboard and import
      |it in into the editor""".stripMargin)
    itemFromClipboard.addListener(SWT.Selection, e => interactiveImportFromClipboard(false))
  }

  override protected def validateAndGet(nameValPairs: IndexedSeq[(String, String)]): ListMap[String, String] = {
    val nameValPairs2 = nameValPairs filter {
      case (k, v) => !k.isEmpty // Value can be empty
    }

    val duplicates = nameValPairs2.groupBy(_._1).collect { case (n, vs) if vs.size > 1 => (n, vs.size) }
    requireFriendly(duplicates.size == 0, "Duplicate keys: " + duplicates.keys.mkString(", "))
    nameValPairs2.foreach {
      case (k, v) => HttpUtils.validateCookieCharacterSet(k, v)
    }
    ListMap(nameValPairs2: _*)
  }

  /**
   * Try to (interactively) import cookies from clipboard, showing confirmations and errors
   * @param silent if `true`, only proceed with import if no user input is needed
   */
  private def interactiveImportFromClipboard(silent: Boolean): Unit = {
    val title = "Cookies Import"
    try {
      val cookieString = getStringFromClipboard()
      val cookiesMap = HttpUtils.parseClientCookies(cookieString)

      if ((initialCookiesMap.isEmpty) || (
        !silent
        && MessageDialog.openConfirm(parent, title, "Do you want to replace current cookies with the clipboard content?")
      )) {
        renderContent(cookiesMap)
      }
    } catch {
      case NonFatal(ex) if silent => // NOOP
      case NonFatal(ex)           => MessageDialog.openError(shell, title, "Clipboard doesn't contain a valid cookie string")
    }
  }
}
