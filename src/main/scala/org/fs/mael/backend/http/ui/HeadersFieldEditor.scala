package org.fs.mael.backend.http.ui

import scala.collection.immutable.ListMap

import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Shell
import org.fs.mael.backend.http.utils.HttpUtils
import org.fs.mael.backend.http.utils.SimpleTableSerializer
import org.fs.mael.ui.components.StringTableFieldEditor
import org.slf4s.Logging

class HeadersFieldEditor(name: String, labelText: String, parent: Composite)
  extends StringTableFieldEditor(name, labelText, parent)
  with Logging {

  override protected def toClipboardString(selected: Seq[(String, String)]): String = {
    selected map { case (k, v) => k + ": " + v } mkString ("\n")
  }

  override protected def serialize(headersMap: ListMap[String, String]): String = {
    HttpUtils.validateHeadersCharacterSet(headersMap)
    SimpleTableSerializer.serialize(headersMap)
  }

  override protected def deserialize(headersSerialString: String): ListMap[String, String] =
    SimpleTableSerializer.deserialize(headersSerialString)

  override protected def createPopupEditorDialog(shell: Shell, initialContent: ListMap[String, String]): HeadersFieldEditorDialog =
    new HeadersFieldEditorDialog(shell, initialContent)
}
