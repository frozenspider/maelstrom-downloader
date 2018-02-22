package org.fs.mael.ui

import org.eclipse.jface.preference.PreferenceStore
import org.eclipse.jface.preference.{ PreferenceManager => JPreferenceManager }
import org.eclipse.swt.widgets.Shell
import org.eclipse.jface.preference.PreferenceDialog
import org.fs.mael.core.CoreUtils._
import org.eclipse.jface.preference.PreferenceNode
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.preference.ScaleFieldEditor
import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.FontFieldEditor
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.jface.preference.FileFieldEditor
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.jface.preference.PathEditor
import org.eclipse.jface.preference.StringFieldEditor
import org.eclipse.jface.preference.ColorFieldEditor
import java.io.FileNotFoundException

class PreferenceManager {
  val store = new PreferenceStore().withCode { store => // TODO: Link to file
    store.setFilename("testfile.prefs")
    try {
      store.load()
    } catch {
      case ex: FileNotFoundException => // NOOP
    }
  }

  val mgr = new JPreferenceManager().withCode { mgr =>
    val node1 = new PreferenceNode("p1", "label1", null, classOf[PreferenceManager.FieldEditorPageOne].getName)
    val node2 = new PreferenceNode("p2", "label2", null, classOf[PreferenceManager.FieldEditorPageTwo].getName)
    mgr.addToRoot(node1)
    mgr.addToRoot(node2)
  }

  def showDialog(parent: Shell): Unit = {
    val dlg = new PreferenceDialog(parent, mgr)
    dlg.setPreferenceStore(store)
    dlg.open()
  }

}
object PreferenceManager {
  // Use the "flat" layout
  class FieldEditorPageOne extends FieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {

    /**
     * Creates the field editors
     */
    def createFieldEditors(): Unit = {
      // Add a boolean field
      val bfe = new BooleanFieldEditor("myBoolean", "Boolean", getFieldEditorParent());
      addField(bfe);

      // Add a color field
      val cfe = new ColorFieldEditor("myColor", "Color:", getFieldEditorParent());
      addField(cfe);

      // Add a directory field
      val dfe = new DirectoryFieldEditor("myDirectory", "Directory:", getFieldEditorParent());
      addField(dfe);

      // Add a file field
      val ffe = new FileFieldEditor("myFile", "File:", getFieldEditorParent());
      addField(ffe);

      // Add a font field
      val fontFe = new FontFieldEditor("myFont", "Font:", getFieldEditorParent());
      addField(fontFe);

      // Add a radio group field
      val rfe = new RadioGroupFieldEditor(
        "myRadioGroup",
        "Radio Group", 2, Array(
          Array("First Value", "first"),
          Array("Second Value", "second"),
          Array("Third Value", "third"),
          Array("Fourth Value", "fourth")
        ), getFieldEditorParent(),
        true
      );
      addField(rfe);

      // Add a path field
      val pe = new PathEditor("myPath", "Path:", "Choose a Path", getFieldEditorParent());
      addField(pe);

      // Add an integer field
      val ife = new IntegerFieldEditor("myInt", "Int:", getFieldEditorParent());
      addField(ife);

      // Add a scale field
      val sfe = new ScaleFieldEditor("myScale", "Scale:", getFieldEditorParent(), 0, 100, 1, 10);
      addField(sfe);

      // Add a string field
      val stringFe = new StringFieldEditor("myString", "String:", getFieldEditorParent());
      addField(stringFe);

    }
  }

  // Use the "grid" layout
  class FieldEditorPageTwo extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) {

    def createFieldEditors(): Unit = {
      // Add an integer field
      val ife = new IntegerFieldEditor("myInt", "Int:", getFieldEditorParent());
      addField(ife);

      // Add a scale field
      val sfe = new ScaleFieldEditor("myScale", "Scale:", getFieldEditorParent(), 0, 100, 1, 10);
      addField(sfe);

      // Add a string field
      val stringFe = new StringFieldEditor("myString", "String:", getFieldEditorParent());
      addField(stringFe);
    }
  }

}
