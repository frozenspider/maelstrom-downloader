package org.fs.mael.backend.http.ui

import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.StringFieldEditor
import org.fs.mael.ui.prefs.MFieldEditorPreferencePage
import org.fs.mael.backend.http.HttpBackend

object HttpPreferences {
  import org.fs.mael.core.config.ConfigSetting._

  private val prefix = HttpBackend.Id

  val UserAgent: OptionalStringConfigSetting =
    OptionalStringConfigSetting(prefix + ".userAgent", None)

  class HeadersPage extends MFieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
    override def createFieldEditors(): Unit = {
      row(UserAgent) { (setting, parent) =>
        new StringFieldEditor(setting.id, "User-Agent:", parent)
      }
    }
  }
}
