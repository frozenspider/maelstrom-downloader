package org.fs.mael.ui.config

import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.swt.widgets.Composite
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.ConfigSetting

abstract class MFieldEditorPreferencePage(style: Int) extends FieldEditorPreferencePage(style) {
  /** Making this method visible */
  override def noDefaultAndApplyButton(): Unit = super.noDefaultAndApplyButton()

  /**
   * Keep track of field editors created on this page as well as their parents.
   * Please use this if an element is added manually rather than through helpers defined here
   */
  protected var _fieldEditorsWithParents: IndexedSeq[(FieldEditor, Composite)] = IndexedSeq.empty

  /**
   * Initialize a default value for the given config setting.
   * Please use this if an element is added manually rather than through helpers defined here
   */
  protected def initSetting(setting: ConfigSetting[_]): Unit = {
    ConfigManager.initDefault(getPreferenceStore, setting)
  }

  def fieldEditorsWithParents: IndexedSeq[(FieldEditor, Composite)] = _fieldEditorsWithParents

  def row[CS <: ConfigSetting[_], FE <: FieldEditor](setting: CS)(createEditor: (CS, Composite) => FE): FE = {
    initSetting(setting)
    val parent = getFieldEditorParent
    val editor = createEditor(setting, parent)
    addField(editor)
    _fieldEditorsWithParents = _fieldEditorsWithParents :+ (editor, parent)
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
