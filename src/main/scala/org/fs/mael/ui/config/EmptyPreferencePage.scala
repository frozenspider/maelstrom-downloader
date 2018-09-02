package org.fs.mael.ui.config

import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.fs.mael.core.config.IConfigStore

class EmptyPreferencePage[C <: IConfigStore] extends MFieldEditorPreferencePage[C](FieldEditorPreferencePage.FLAT) {
  def createFieldEditors(): Unit = {}
}
