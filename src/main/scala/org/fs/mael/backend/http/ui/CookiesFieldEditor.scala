package org.fs.mael.backend.http.ui

import scala.collection.immutable.ListMap

import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Shell
import org.fs.mael.backend.http.utils.SimpleTableSerializer
import org.fs.mael.ui.components.StringTableFieldEditor

class CookiesFieldEditor(name: String, labelText: String, parent: Composite)
  extends StringTableFieldEditor(name, labelText, parent) {

  override protected def serialize(cookiesMap: ListMap[String, String]): String =
    SimpleTableSerializer.serialize(cookiesMap)

  override protected def deserialize(cookiesSerialString: String): ListMap[String, String] =
    SimpleTableSerializer.deserialize(cookiesSerialString)

  override protected def createPopupEditorDialog(shell: Shell, initialContent: ListMap[String, String]): CookiesFieldEditorDialog =
    new CookiesFieldEditorDialog(shell, initialContent)
}
