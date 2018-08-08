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
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.LocalConfigSettingValue
import org.eclipse.jface.preference.FieldEditorPreferencePage

/** Field editor for global proxy settings */
class ProxyLocalFieldEditor(
  labelText:            String,
  proxyLocalRefSetting: LocalEntityConfigSetting[Proxy],
  proxiesSetting:       ConfigSetting[Seq[Proxy]],
  _parent:              Composite
) extends FieldEditor with ConfigAware[BackendConfigStore] {
  setLabelText(labelText)
  createControl(_parent)

  private var loadComplete: Boolean = false
  private var proxies: Seq[Proxy] = _
  private var selectedProxyValue: LocalConfigSettingValue[Proxy] = _
  private var defaultProxy: Proxy = _

  private var top: Composite = _
  private var dropdown: Combo = _
  private var rbDefault: Button = _
  private var rbDefined: Button = _
  private var rbOther: Button = _
  private var editor: ProxyEditorComponent = _

  override def doLoad(): Unit = {
    loadComplete = true
    selectedProxyValue = cfg(proxyLocalRefSetting)
    defaultProxy = cfg.globalCfg.resolve(proxyLocalRefSetting.defaultSetting)
    proxies = cfg.globalCfg(proxiesSetting)
    if (proxies.isEmpty || !proxies.contains(Proxy.NoProxy)) {
      this.proxies = Proxy.NoProxy +: proxies
    } else {
      this.proxies = proxies
    }
    this.editor.parentPageOption = pageOption
    render()
  }

  private def pageOption = Option(getPage().asInstanceOf[FieldEditorPreferencePage])

  override def doLoadDefault(): Unit = {
    throw new UnsupportedOperationException("No defaults for proxy settings!")
  }

  override def doStore(): Unit = {
    cfg.set(proxyLocalRefSetting, selectedProxyValue)
  }

  override def getNumberOfControls: Int = 1

  override def doFillIntoGrid(parent: Composite, numColumns: Int): Unit = {
    top = parent
    doFillIntoGrid(numColumns)
  }

  private def doFillIntoGrid(numColumns: Int): Unit = {
    top.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false).withCode { gridData =>
      gridData.horizontalSpan = numColumns
    })

    val group = new Group(top, SWT.NONE).withCode { group =>
      group.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, true))
      group.setLayout(new GridLayout)
      group.setText(labelText)
    }

    rbDefault = new Button(group, SWT.RADIO | SWT.LEFT).withCode { btn =>
      btn.setText("Application default")
      btn.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false))
    }
    rbDefault.addSelectionListener(toSelectionListener(e => onDefaultSelected()))

    rbDefined = new Button(group, SWT.RADIO | SWT.LEFT).withCode { btn =>
      btn.setText("Pre-configured")
      btn.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false))
    }
    rbDefined.addSelectionListener(toSelectionListener(e => onDefinedSelected()))

    dropdown = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY).withCode { dd =>
      dd.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false).withCode { layout =>
        layout.horizontalIndent = 20
      })
    }

    rbOther = new Button(group, SWT.RADIO | SWT.LEFT).withCode { btn =>
      btn.setText("Other")
      btn.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false))
    }
    rbOther.addSelectionListener(toSelectionListener(e => onOtherSelected()))

    editor = new ProxyEditorComponent(group, new GridData(SWT.FILL, SWT.BEGINNING, true, false).withCode { layout =>
      layout.horizontalIndent = 20
    }, false, None)
  }

  override def adjustForNumColumns(numColumns: Int): Unit = {
    (top.getLayoutData.asInstanceOf[GridData]).horizontalSpan = numColumns
  }

  override def isValid(): Boolean = {
    tryUpdateCachedValue()
  }

  private def tryUpdateCachedValue(): Boolean = loadComplete && {
    val errorMsgOption: Option[String] = if (rbDefault.getSelection) {
      selectedProxyValue = LocalConfigSettingValue.Default
      None
    } else if (rbDefined.getSelection) {
      selectedProxyValue = LocalConfigSettingValue.Ref(proxies(dropdown.getSelectionIndex).uuid)
      None
    } else if (rbOther.getSelection) {
      val result = editor.validate()
      if (result.isEmpty) {
        selectedProxyValue = LocalConfigSettingValue.Embedded(editor.value)
      }
      result
    } else {
      Some("Invalid proxy editor state!")
    }
    pageOption map (_.setErrorMessage(errorMsgOption getOrElse null))
    errorMsgOption.isEmpty
  }

  private def onDefaultSelected(): Unit = {
    dropdown.setEnabled(false)
    editor.setEditAllowed(false)
  }

  private def onDefinedSelected(): Unit = {
    dropdown.setEnabled(true)
    editor.setEditAllowed(false)
  }

  private def onOtherSelected(): Unit = {
    dropdown.setEnabled(false)
    editor.setEditAllowed(true)
  }

  private def render(): Unit = {
    rbDefault.setText(s"Application default (${defaultProxy.name})")
    dropdown.setItems((proxies map (_.name)): _*)
    selectedProxyValue match {
      case LocalConfigSettingValue.Default =>
        rbDefault.setSelection(true)
        dropdown.select(0)
        editor.renderNew(UUID.randomUUID(), "Custom")
        onDefaultSelected()
      case LocalConfigSettingValue.Ref(uuid) =>
        val idx = proxies.indexWhere(_.uuid == uuid)
        if (idx == -1) {
          selectedProxyValue = LocalConfigSettingValue.Default
          render()
        } else {
          rbDefined.setSelection(true)
          dropdown.select(idx)
          editor.renderNew(UUID.randomUUID(), "Custom")
          onDefinedSelected()
        }
      case LocalConfigSettingValue.Embedded(proxy) =>
        rbOther.setSelection(true)
        dropdown.select(0)
        editor.render(proxy)
        onOtherSelected()
    }
  }
}
