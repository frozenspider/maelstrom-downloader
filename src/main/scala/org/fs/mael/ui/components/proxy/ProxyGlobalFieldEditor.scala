package org.fs.mael.ui.components.proxy

import java.util.UUID

import org.eclipse.jface.layout.TableColumnLayout
import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.viewers.ColumnWeightData
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.config.ConfigSetting
import org.fs.mael.core.config.ConfigSetting._
import org.fs.mael.core.config.proxy.Proxy
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.components.ConfigAware
import org.fs.mael.ui.utils.SwtUtils._

/** Field editor for global proxy settings */
class ProxyGlobalFieldEditor(
  labelText:              String,
  proxySetting:           ConfigSetting[Seq[Proxy]],
  defaultProxyRefSetting: RefConfigSetting[Proxy],
  _parent:                Composite
) extends FieldEditor with ConfigAware {
  setLabelText(labelText)
  createControl(_parent)

  private var proxies: Seq[Proxy] = _
  private var defaultProxy: Proxy = _
  private var selectedProxy: Proxy = _

  private var top: Composite = _
  /** Implemented as a table because list items can't be styled */
  private var listTable: Table = _
  private var btnNew: Button = _
  private var btnMarkAsDefault: Button = _
  private var btnDelete: Button = _
  private var editor: ProxyEditorComponent = _

  override def doLoad(): Unit = {
    val proxies = cfg(proxySetting)
    if (proxies.isEmpty || !proxies.contains(Proxy.NoProxy)) {
      this.proxies = Proxy.NoProxy +: proxies
    } else {
      this.proxies = proxies
    }
    this.defaultProxy = cfg.resolve(defaultProxyRefSetting)
    this.editor.parentPageOption = Option(getPage().asInstanceOf[PreferencePage])
    selectNoProxy()
  }

  override def doLoadDefault(): Unit = {
    throw new UnsupportedOperationException("No defaults for proxy settings!")
  }

  override def doStore(): Unit = {
    cfg.set(proxySetting, proxies)
    cfg.set(defaultProxyRefSetting, defaultProxy.uuid)
  }

  override def getNumberOfControls: Int = 2

  override def doFillIntoGrid(parent: Composite, numColumns: Int): Unit = {
    top = parent
    doFillIntoGrid(numColumns)
  }

  private def doFillIntoGrid(numColumns: Int): Unit = {
    top.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true).withCode { gridData =>
      gridData.horizontalSpan = numColumns
    })

    val leftPanel = new Composite(top, SWT.NONE).withCode { panel =>
      panel.setLayoutData(new GridData(SWT.BEGINNING, SWT.FILL, false, true))
      panel.setLayout(new GridLayout)
    }

    // A hack to make a single-column table
    // See https://stackoverflow.com/a/25660919/466646
    val tcLayout = new TableColumnLayout
    val tablePanel = new Composite(leftPanel, SWT.NONE).withCode { panel =>
      panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
      panel.setLayout(tcLayout)
    }
    listTable = new Table(tablePanel, SWT.SINGLE | SWT.BORDER).withCode { listTable =>
      val tc = new TableColumn(listTable, SWT.NONE)
      tcLayout.setColumnData(tc, new ColumnWeightData(10))
    }
    listTable.addSelectionListener(toSelectionListener(onListElementSelected))

    btnNew = new Button(leftPanel, SWT.NONE).withCode { btn =>
      btn.setText("New")
      btn.setLayoutData(new GridData(SWT.FILL, SWT.TRAIL, true, false))
    }
    btnNew.addSelectionListener(toSelectionListener(onNewClicked))

    btnMarkAsDefault = new Button(leftPanel, SWT.NONE).withCode { btn =>
      btn.setText("Mark as Default")
      btn.setLayoutData(new GridData(SWT.FILL, SWT.TRAIL, true, false))
    }
    btnMarkAsDefault.addSelectionListener(toSelectionListener(onMarkAsDefaultClicked))

    btnDelete = new Button(leftPanel, SWT.NONE).withCode { btn =>
      btn.setText("Delete")
      btn.setLayoutData(new GridData(SWT.FILL, SWT.TRAIL, true, false))
    }
    btnDelete.addSelectionListener(toSelectionListener(onDeleteClicked))

    editor = new ProxyEditorComponent(top, new GridData(SWT.FILL, SWT.FILL, true, true).withCode { gd =>
      gd.minimumWidth = 200
    }, saveProxy)
  }

  override def adjustForNumColumns(numColumns: Int): Unit = {
    (top.getLayoutData.asInstanceOf[GridData]).horizontalSpan = numColumns
  }

  /** When list selection changes */
  private def onListElementSelected(e: SelectionEvent): Unit = {
    if (listTable.getSelectionCount != 1) {
      listTable.select(0)
    }
    val proxy = proxies(listTable.getSelectionIndex)

    btnMarkAsDefault.setEnabled(proxy != defaultProxy)
    if (selectedProxy != proxy) {
      selectedProxy = proxy
      editor.render(proxy)
    }

    btnDelete.setEnabled(proxy.isInstanceOf[Proxy.CustomProxy])
  }

  private def onNewClicked(e: SelectionEvent): Unit = {
    val newNamesStream: Stream[String] = {
      def enumeratedNamesStream(i: Int): Stream[String] = s"New ($i)" #:: enumeratedNamesStream(i + 1)
      "New" #:: enumeratedNamesStream(2)
    }
    val name = newNamesStream.find(name => !proxies.exists(_.name == name)).get
    val uuid = UUID.randomUUID()
    editor.renderNew(uuid, name)
  }

  private def onDeleteClicked(e: SelectionEvent): Unit =
    if (selectedProxy != Proxy.NoProxy) {
      proxies = proxies.filter(_ != selectedProxy)
      if (defaultProxy == selectedProxy) {
        defaultProxy = Proxy.NoProxy
      }
      selectNoProxy()
    }

  private def onMarkAsDefaultClicked(e: SelectionEvent): Unit = {
    defaultProxy = proxies(listTable.getSelectionIndex)
    rerenderList()
  }

  private def saveProxy(proxy: Proxy): Unit = {
    proxies.indexOf(proxy) match {
      case -1 => proxies = proxies :+ proxy
      case i  => proxies = proxies.updated(i, proxy)
    }
    if (defaultProxy == proxy) {
      defaultProxy = proxy
    }
    selectedProxy = proxy
    rerenderList()
  }

  private def selectNoProxy(): Unit = {
    selectedProxy = Proxy.NoProxy
    rerenderList()
    editor.render(selectedProxy)
  }

  private def rerenderList(): Unit = {
    listTable.removeAll()
    proxies.foreach { proxy =>
      val row = new TableItem(listTable, SWT.NONE)
      row.setData(proxy)
      row.setText(proxy.name)
      if (proxy == defaultProxy) {
        row.setFont(row.getFont.bold)
      }
    }
    listTable.getColumns.foreach(_.pack())
    val selectedProxyIdx = proxies.indexOf(selectedProxy) match {
      case -1 => 0 // Select "No Proxy"
      case x  => x
    }
    listTable.select(selectedProxyIdx)
    listTable.notifyListeners(SWT.Selection, new Event())
  }
}
