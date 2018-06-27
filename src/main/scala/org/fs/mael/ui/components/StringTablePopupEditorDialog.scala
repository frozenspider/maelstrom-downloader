package org.fs.mael.ui.components

import scala.collection.immutable.ListMap

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

abstract class StringTablePopupEditorDialog(title: String, parent: Shell, initial: ListMap[String, String])
  extends PopupEditorDialog[ListMap[String, String]](title, parent, initial) { this: Logging =>

  private var editors: IndexedSeq[(Text, Text, Button)] = IndexedSeq.empty

  override protected def initBeforeMainArea() = {
    // Since tooltips for top-level MenuItem doesn't work, we're using surrogate menu bar
    val topButtonRow = new Composite(shell, SWT.NONE).withCode { composite =>
      composite.setLayout((new RowLayout).withCode { layout =>
        layout.marginTop = 0
        layout.marginBottom = 0
        layout.marginRight = 0
        layout.marginLeft = 0
      })
      composite.setLayoutData(new GridData(SWT.FILL, SWT.LEAD, true, false))
    }

    fillTopButtonsInto(topButtonRow)
  }

  protected def fillTopButtonsInto(topButtonRow: Composite): Unit = {
    val itemAdd = new Button(topButtonRow, SWT.PUSH)
    itemAdd.setText("Add new row")
    itemAdd.addListener(SWT.Selection, e => {
      appendRow("", "")
      updateScroll()
    })
  }

  protected def appendRow(k: String, v: String): Unit = {
    val removeBtn = new Button(dataPane, SWT.NONE)
    val nameEditor = new Text(dataPane, SWT.BORDER)
    val valueEditor = new Text(dataPane, SWT.BORDER)
    val tuple = (nameEditor, valueEditor, removeBtn)

    nameEditor.setLayoutData(new GridData(SWT.FILL, SWT.LEAD, true, false))
    nameEditor.setText(k)
    nameEditor.setToolTipText(nameColumnHeader)
    valueEditor.setLayoutData(new GridData(SWT.FILL, SWT.LEAD, true, false))
    valueEditor.setText(v)
    valueEditor.setToolTipText(valueColumnHeader)
    removeBtn.setText("-")
    removeBtn.setToolTipText(removeEntryTooltipText)
    removeBtn.setFont(new Font(parent.getDisplay, monospacedFontData))
    removeBtn.setLayoutData(new GridData(SWT.LEAD, SWT.LEAD, false, false).withCode { gd =>
      val sz = valueEditor.computeSize(SWT.DEFAULT, SWT.DEFAULT)
      gd.widthHint = sz.y
      gd.heightHint = sz.y
    })
    removeBtn.addListener(SWT.Selection, e => {
      editors = editors filter { case (_, _, btn2) => btn2 != removeBtn }
      tuple.productIterator.foreach { case w: Widget => w.dispose() }
      updateScroll()
    })
    editors = editors :+ tuple
  }

  protected def renderContent(content: ListMap[String, String]): Unit = {
    dataPane.getChildren foreach (_.dispose)
    editors = IndexedSeq.empty
    val keyValueSeq = content.toSeq :+ ("", "")
    keyValueSeq foreach {
      case (k, v) => appendRow(k, v)
    }
    updateScroll()
  }

  /** Get a result value from this editor, throwing UserFriendlyException if validation fails */
  protected def getResultValue(): ListMap[String, String] = {
    val nameValPairs = editors.map {
      case (nameEditor, valueEditor, _) => (nameEditor.getText.trim, valueEditor.getText.trim)
    }
    validateAndGet(nameValPairs)
  }

  protected lazy val nameColumnHeader: String = "Name"
  protected lazy val valueColumnHeader: String = "Value"
  protected lazy val removeEntryTooltipText: String = "Remove entry"

  protected def validateAndGet(nameValPairs: IndexedSeq[(String, String)]): ListMap[String, String]
}
