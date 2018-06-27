package org.fs.mael.backend.http.ui

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global

import org.eclipse.jface.preference.FieldEditor
import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.backend.http.CookiesConfigSetting
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.utils.SwtUtils._

class CookiesFieldEditor(name: String, labelText: String, parent: Composite)
  extends FieldEditor(name, labelText, parent) {

  private var cookiesMap: ListMap[String, String] = ListMap.empty
  private var top: Composite = _
  private var table: Table = _
  private var control: Composite = _
  private var editBtn: Button = _

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
      val itemsString = CookiesConfigSetting.serialize(cookiesMap)
      getPreferenceStore().setValue(getPreferenceName, itemsString)
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
    Option(table).foreach(_.setEnabled(enabled))
    Option(editBtn).foreach(_.setEnabled(enabled))
  }

  private def loadMapFromString(itemsString: String): Unit = {
    cookiesMap = CookiesConfigSetting.deserialize(itemsString)
    renderCookies()
  }

  private def renderCookies(): Unit = {
    table.removeAll()
    cookiesMap.foreach { entry =>
      val row = new TableItem(table, SWT.NONE)
      row.setText(0, entry._1)
      row.setText(1, entry._2)
    }
    table.getColumns().foreach(_.pack())
  }

  def openEditor(): Unit = {
    val dialog = new CookiesFieldEditorDialog(top.getShell, cookiesMap)
    dialog.prompt().foreach {
      case Some(newCookiesMap) =>
        cookiesMap = newCookiesMap
        syncExecSafely(parent) {
          renderCookies()
        }
      case None => // NOOP
    }
  }
}
