package org.fs.mael.core.backend.ui

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.TabFolder
import org.eclipse.swt.widgets.TabItem
import org.fs.mael.core.config.ConfigStore
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor
import org.fs.mael.ui.utils.SwtUtils

class BackendConfigUiImpl(
  override val backendId:        String,
  override val isEditable:       Boolean,
  override val cfgOption:        Option[ConfigStore],
  override val globalCfg:        ConfigStore,
  override val tabFolder:        TabFolder,
  override val pageDescriptions: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]]
) extends AbstractBackendConfigUi {

  override def createPage[T <: MFieldEditorPreferencePage](pageDescr: MPreferencePageDescriptor[T], tabFolder: TabFolder): T = {
    val tab = new TabItem(tabFolder, SWT.NONE)
    tab.setText(pageDescr.name)
    val container = new Composite(tabFolder, SWT.NONE)
    container.setLayout(new GridLayout())
    tab.setControl(container)

    val page = pageDescr.clazz.newInstance()
    page.setConfigStore(cfg)
    page.noDefaultAndApplyButton()
    page.createControl(container)
    page.getControl.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true))
    if (!isEditable) {
      page.fieldEditorsWithParents.foreach {
        case (editor, parent) => SwtUtils.disable(editor, parent)
      }
    }
    page
  }
}
