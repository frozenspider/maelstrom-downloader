package org.fs.mael.ui.config

import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.swt.widgets.Composite
import org.fs.mael.core.config.ConfigSetting
import org.fs.mael.core.config.IConfigStore
import org.fs.mael.ui.components.ConfigAware

abstract class MFieldEditorPreferencePage[C <: IConfigStore](style: Int) extends FieldEditorPreferencePage(style) {
  /** Making this method visible */
  override def noDefaultAndApplyButton(): Unit = super.noDefaultAndApplyButton()

  /** Making this method visible */
  override def checkState(): Unit = super.checkState()

  /**
   * Keep track of field editors created on this page as well as their parents.
   * Please use this if an element is added manually rather than through helpers defined here
   */
  protected var _fieldEditorsWithParents: IndexedSeq[(FieldEditor, Composite)] = IndexedSeq.empty
  protected var _cfg: C = _

  /**
   * Initialize a default value for the given config setting.
   * Please use this if an element is added manually rather than through helpers defined here
   */
  protected def initSetting(setting: ConfigSetting[_]): Unit = {
    require(_cfg != null, "Config store has not been initialized, invoke setConfigStore first")
    _cfg.initDefault(setting)
  }

  def setConfigStore(cfg: C): Unit = {
    _cfg = cfg
    super.setPreferenceStore(cfg.inner)
  }

  override protected def initialize(): Unit = {
    _fieldEditorsWithParents.foreach(_._1 match {
      case e: ConfigAware[C] => e.cfg = _cfg
      case _                 => // NOOP
    })
    super.initialize()
  }

  override def setPreferenceStore(store: IPreferenceStore): Unit = {
    throw new UnsupportedOperationException("Use setConfigStore instead")
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

  def customRow[FE <: FieldEditor](settings: ConfigSetting[_]*)(createEditor: (Composite) => FE): FE = {
    settings foreach initSetting
    val parent = getFieldEditorParent
    val editor = createEditor(parent)
    addField(editor)
    _fieldEditorsWithParents = _fieldEditorsWithParents :+ (editor, parent)
    editor
  }
}
