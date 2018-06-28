package org.fs.mael.backend.http.ui

import scala.collection.immutable.ListMap

import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Shell
import org.fs.mael.ui.components.StringTableFieldEditor

class HeadersFieldEditor(name: String, labelText: String, parent: Composite)
  extends StringTableFieldEditor(name, labelText, parent) {

  override protected def serialize(cookiesMap: ListMap[String, String]): String =
    "" // FIXME

  override protected def deserialize(cookiesMapString: String): ListMap[String, String] =
    ListMap.empty // FIXME

  override protected def createPopupEditorDialog(shell: Shell, initialContent: ListMap[String, String]): HeadersFieldEditorDialog =
    new HeadersFieldEditorDialog(shell, initialContent)
}
