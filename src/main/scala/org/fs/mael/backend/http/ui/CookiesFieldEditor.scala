package org.fs.mael.backend.http.ui

import scala.collection.immutable.ListMap

import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Shell
import org.fs.mael.backend.http.utils.HttpUtils
import org.fs.mael.backend.http.utils.SimpleTableSerializer
import org.fs.mael.ui.components.StringTableFieldEditor
import org.slf4s.Logging

class CookiesFieldEditor(name: String, labelText: String, parent: Composite)
  extends StringTableFieldEditor(name, labelText, parent)
  with Logging {

  override protected def toClipboardString(selected: Seq[(String, String)]): String = {
    val cookieString = selected map { case (k, v) => k + "=" + v } mkString ("; ")
    "Cookie: " + cookieString
  }

  override protected def serialize(cookiesMap: ListMap[String, String]): String = {
    HttpUtils.validateCookiesCharacterSet(cookiesMap)
    SimpleTableSerializer.serialize(cookiesMap)
  }

  override protected def deserialize(cookiesSerialString: String): ListMap[String, String] =
    SimpleTableSerializer.deserialize(cookiesSerialString)

  override protected def createPopupEditorDialog(shell: Shell, initialContent: ListMap[String, String]): CookiesFieldEditorDialog =
    new CookiesFieldEditorDialog(shell, initialContent)
}
