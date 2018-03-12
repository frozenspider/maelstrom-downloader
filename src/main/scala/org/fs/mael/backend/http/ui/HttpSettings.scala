package org.fs.mael.backend.http.ui

import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.StringFieldEditor
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.backend.http.HttpBackend

object HttpSettings {
  import org.fs.mael.core.config.ConfigSetting
  import org.fs.mael.core.config.ConfigSetting.RadioConfigSetting
  import org.fs.mael.core.config.ConfigSetting.RadioValue

  private val prefix = HttpBackend.Id

  val UserAgent: ConfigSetting[Option[String]] =
    ConfigSetting(prefix + ".userAgent", None)

  class HeadersPage extends MFieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
    override def createFieldEditors(): Unit = {
      row(UserAgent) { (setting, parent) =>
        new StringFieldEditor(setting.id, "User-Agent:", parent)
      }
    }
  }
}
