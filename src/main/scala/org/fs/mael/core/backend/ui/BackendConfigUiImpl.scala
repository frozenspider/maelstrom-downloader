package org.fs.mael.core.backend.ui

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.TabFolder
import org.eclipse.swt.widgets.TabItem
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.config.SettingsAccessChecker
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor
import org.fs.mael.ui.utils.SwtUtils

class BackendConfigUiImpl(
  override val resultCfg:        BackendConfigStore,
  override val isEditable:       Boolean,
  override val cfgOption:        Option[BackendConfigStore],
  override val globalCfg:        IGlobalConfigStore,
  override val tabFolder:        TabFolder,
  override val pageDescriptions: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage[BackendConfigStore]]]
) extends AbstractBackendConfigUi {

  override def createPage[T <: MFieldEditorPreferencePage[BackendConfigStore]](
    pageDescr: MPreferencePageDescriptor[T],
    tabFolder: TabFolder
  ): T = {
    val tab = new TabItem(tabFolder, SWT.NONE)
    tab.setText(pageDescr.name)
    val container = new Composite(tabFolder, SWT.NONE)
    container.setLayout(new GridLayout())
    tab.setControl(container)

    val page = pageDescr.clazz.newInstance()
    page.setConfigStore(resultCfg)
    page.noDefaultAndApplyButton()
    page.createControl(container)
    page.getControl.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true))
    if (!isEditable) {
      page.fieldEditorsWithParents.foreach {
        case (editor, parent) => SwtUtils.setEnabled(editor, parent, false)
      }
    }
    page
  }
}
