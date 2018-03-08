package org.fs.mael.ui.prefs

import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.swt.widgets.Composite
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.ConfigSetting

abstract class MFieldEditorPreferencePage(style: Int) extends FieldEditorPreferencePage(style) {
  /**
   * Initialize a default value for the given config setting.
   * Please use this if an element is added manually rather than through helpers defined here
   */
  protected def initSetting(setting: ConfigSetting[_]): Unit = {
    ConfigManager.initDefault(getPreferenceStore, setting)
  }

  def row[CS <: ConfigSetting[_], FE <: FieldEditor](setting: CS)(createEditor: (CS, Composite) => FE): FE = {
    initSetting(setting)
    val editor = createEditor(setting, getFieldEditorParent)
    addField(editor)
    editor
  }

  def radioRow[RV <: ConfigSetting.RadioValue](title: String, setting: ConfigSetting.RadioConfigSetting[RV]): RadioGroupFieldEditor = {
    row(setting) { (setting, parent) =>
      new RadioGroupFieldEditor(
        setting.id, title, setting.values.size,
        setting.values.map { o => Array(o.prettyName, o.id) }.toArray,
        parent, true
      )
    }
  }
}
