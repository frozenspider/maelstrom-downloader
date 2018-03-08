package org.fs.mael.ui.prefs

import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.swt.widgets.Composite
import org.fs.mael.core.config.ConfigOption

abstract class RichFieldEditorPreferencePage(style: Int) extends FieldEditorPreferencePage(style) {
  def row[CO <: ConfigOption[_], FE <: FieldEditor](option: CO)(createEditor: (CO, Composite) => FE): FE = {
    val editor = createEditor(option, getFieldEditorParent)
    addField(editor)
    editor
  }

  def radioRow[RV <: ConfigOption.RadioValue](title: String, option: ConfigOption.RadioConfigOption[RV]): RadioGroupFieldEditor = {
    row(option) { (option, parent) =>
      new RadioGroupFieldEditor(
        option.id, title, option.values.size,
        option.values.map { o => Array(o.prettyName, o.id) }.toArray,
        parent, true
      )
    }
  }
}
