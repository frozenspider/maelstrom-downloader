package org.fs.mael.ui.config

import org.eclipse.jface.preference.FieldEditorPreferencePage

class EmptyPreferencePage extends MFieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
  def createFieldEditors(): Unit = {}
}
