package org.fs.mael.backend.http.ui

import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.StringFieldEditor
import org.fs.mael.ui.prefs.MFieldEditorPreferencePage

object HttpPreferences {
  import org.fs.mael.core.config.ConfigSetting._

  val UserAgent: OptionalStringConfigSetting =
    OptionalStringConfigSetting("http.userAgent", None)

  class HeadersPage extends MFieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
    override def createFieldEditors(): Unit = {
      row(UserAgent) { (setting, parent) =>
        new StringFieldEditor(setting.id, "User-Agent:", parent)
      }
    }
  }
}
