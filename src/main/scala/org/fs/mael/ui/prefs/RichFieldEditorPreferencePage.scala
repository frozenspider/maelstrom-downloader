package org.fs.mael.ui.prefs

import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.swt.widgets.Composite
import org.fs.mael.ui.ConfigOptions._

abstract class RichFieldEditorPreferencePage(style: Int) extends FieldEditorPreferencePage(style) {
  def row[CO <: ConfigOption[_], FE <: FieldEditor](option: CO)(createEditor: (CO, Composite) => FE): FE = {
    val editor = createEditor(option, getFieldEditorParent)
    addField(editor)
    editor
  }

  def radioRow[T <: RadioOption](title: String, option: RadioConfigOption[T]): RadioGroupFieldEditor = {
    row(option) { (option, parent) =>
      new RadioGroupFieldEditor(
        option.id, title, option.values.size,
        option.values.map { o => Array(o.prettyName, o.id) }.toArray,
        parent, true
      )
    }
  }
}
