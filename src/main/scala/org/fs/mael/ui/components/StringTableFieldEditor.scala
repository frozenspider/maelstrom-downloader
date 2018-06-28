package org.fs.mael.ui.components

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global

import org.eclipse.jface.preference.FieldEditor
import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.utils.SwtUtils._
import org.fs.mael.ui.utils.Hotkey
import org.fs.mael.ui.utils.Hotkey._
import org.slf4s.Logging

abstract class StringTableFieldEditor(name: String, labelText: String, parent: Composite)
  extends FieldEditor(name, labelText, parent) { this: Logging =>

  private var content: ListMap[String, String] = ListMap.empty
  private var top: Composite = _
  private var table: Table = _
  private var control: Composite = _
  private var editBtn: Button = _

  override def doLoad(): Unit = {
    val contentString = getPreferenceStore().getString(name)
    content = deserialize(contentString)
    rerenderContent()
  }

  override def doLoadDefault(): Unit = {
    val contentString = getPreferenceStore().getDefaultString(name)
    content = deserialize(contentString)
    rerenderContent()
  }

  override def doStore(): Unit = {
    if (!content.isEmpty) {
      val contentString = serialize(content)
      getPreferenceStore().setValue(getPreferenceName, contentString)
    } else {
      getPreferenceStore().setToDefault(getPreferenceName)
    }
  }

  override def getNumberOfControls: Int = 3

  override def doFillIntoGrid(parent: Composite, numColumns: Int): Unit = {
    top = parent
    doFillIntoGrid(numColumns)
  }

  private def doFillIntoGrid(numColumns: Int): Unit = {
    top.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false).withCode { gridData =>
      gridData.horizontalSpan = numColumns
    })

    control = new Composite(parent, SWT.NONE)
    control.setLayout(new GridLayout(1, false).withCode { layout =>
      layout.marginLeft = 0
      layout.marginRight = 0
      layout.marginWidth = 0
      layout.verticalSpacing = 3
    })
    control.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false))

    Option(getLabelText) foreach { text =>
      getLabelControl(control).withCode { label =>
        label.setFont(parent.getFont)
        label.setText(text)
      }
    }

    val inner = new Composite(control, SWT.NONE)
    inner.setLayout(new GridLayout(2, false).withCode { layout =>
      layout.marginLeft = 0
      layout.marginRight = 0
      layout.marginWidth = 0
      layout.marginHeight = 0
    })
    inner.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false))

    table = new Table(inner, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION)
    table.setFont(parent.getFont)
    table.setLinesVisible(true)
    table.setHeaderVisible(true)
    table.addDisposeListener(event => this.table = null)
    table.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false).withCode { gridData =>
      gridData.heightHint = 100
    })

    installTableHotkeys(table)

    val c1 = new TableColumn(table, SWT.NONE)
    c1.setText("Name")

    val c2 = new TableColumn(table, SWT.NONE)
    c2.setText("Value")

    table.getColumns.foreach(_.pack())

    editBtn = new Button(inner, SWT.LEAD).withCode { btn =>
      btn.setFont(parent.getFont)
      btn.setText("Edit...")
      btn.setLayoutData(new GridData(SWT.LEAD, SWT.BEGINNING, false, false))
      btn.addListener(SWT.Selection, e => {
        openEditor()
      })
    }
  }

  override def adjustForNumColumns(numColumns: Int): Unit = {
    (top.getLayoutData.asInstanceOf[GridData]).horizontalSpan = numColumns
  }

  override def setEnabled(enabled: Boolean, parent: Composite): Unit = {
    // We do not check the parent here, nor do we use it at all
    checkParent(control, parent)
    Option(editBtn).foreach(_.setEnabled(enabled))
  }

  private def rerenderContent(): Unit = {
    table.removeAll()
    content.foreach { entry =>
      val row = new TableItem(table, SWT.NONE)
      row.setText(0, entry._1)
      row.setText(1, entry._2)
    }
    table.getColumns().foreach(_.pack())
  }

  def openEditor(): Unit = {
    val dialog = createPopupEditorDialog(top.getShell, content)
    dialog.prompt().foreach {
      case Some(newContent) =>
        content = newContent
        syncExecSafely(parent) {
          rerenderContent()
        }
      case None => // NOOP
    }
  }

  protected def installTableHotkeys(table: Table): Unit = {
    installDefaultHotkeys(table)
    installHotkey(table, Hotkey(Ctrl, Key('C'))) { e =>
      tryShowingError(table.getShell, log) {
        val contentSeq = content.toIndexedSeq
        val selected = table.getSelectionIndices map (contentSeq)
        if (selected.size > 0) {
          Clipboard.copyString(toClipboardString(selected))
        }
      }
      e.doit = false
    }
  }

  protected def serialize(content: ListMap[String, String]): String

  protected def deserialize(contentString: String): ListMap[String, String]

  protected def toClipboardString(selected: Seq[(String, String)]): String

  protected def createPopupEditorDialog(shell: Shell, initialContent: ListMap[String, String]): StringTablePopupEditorDialog
}
