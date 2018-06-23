package org.fs.mael.backend.http.ui

import scala.collection.immutable.ListMap

import org.eclipse.jface.preference.FieldEditor
import org.eclipse.swt.widgets._
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.SWT
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.utils.CoreUtils._
import org.eclipse.swt.layout.GridLayout

class CookiesFieldEditor(name: String, labelText: String, parent: Composite)
    extends FieldEditor(name, labelText, parent) {

  private var cookiesMap: ListMap[String, String] = ListMap.empty
  private var table: Table = _

  override def doLoad(): Unit = {
    val itemsString = getPreferenceStore().getString(name)
    loadMapFromString(itemsString)
  }

  override def doLoadDefault(): Unit = {
    val itemsString = getPreferenceStore().getDefaultString(name)
    loadMapFromString(itemsString)
  }

  override def doStore(): Unit = {
    if (!cookiesMap.isEmpty) {
      val itemsString = CookiesConfigSetting.format(cookiesMap)
      getPreferenceStore().setValue(getPreferenceName, itemsString)
    } else {
      getPreferenceStore().setToDefault(getPreferenceName)
    }
  }

  override def getNumberOfControls: Int = 3

  override def doFillIntoGrid(parent: Composite, numColumns: Int): Unit = {
    parent.getLayoutData.asInstanceOf[GridData].withCode { gridData =>
      gridData.horizontalSpan = numColumns
      gridData.horizontalAlignment = SWT.FILL
      gridData.grabExcessHorizontalSpace = true
    }

    new Label(parent, SWT.LEAD).withCode { label =>
      label.setFont(parent.getFont)
      Option(getLabelText) foreach { text =>
        label.setText(text)
      }
    }

    table = new Table(parent, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION)
    table.setFont(parent.getFont)
    table.setLinesVisible(true)
    table.setHeaderVisible(true)
    table.addDisposeListener(event => this.table = null)
    table.setLayoutData(new GridData(SWT.FILL, SWT.LEAD, true, false))

    val c1 = new TableColumn(table, SWT.NONE)
    c1.setText("Name")

    val c2 = new TableColumn(table, SWT.NONE)
    c2.setText("Value")

    table.getColumns.filter(_.getWidth == 0).map(_.pack())

    new Button(parent, SWT.TRAIL).withCode { btn =>
      btn.setFont(parent.getFont)
      btn.setText("Edit...")
    }
  }

  override def adjustForNumColumns(numColumns: Int): Unit = {
    (table.getParent.getLayoutData.asInstanceOf[GridData]).horizontalSpan = numColumns
  }

  private def loadMapFromString(itemsString: String): Unit = {
    cookiesMap = CookiesConfigSetting.parse(itemsString)
    table.removeAll()
    cookiesMap.foreach { entry =>
      val row = new TableItem(table, SWT.NONE)
      row.setText(0, entry._1)
      row.setText(1, entry._2)
    }
  }
}