package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.backend.ui.BackendConfigUi
import org.fs.mael.core.backend.ui.BackendConfigUiImpl
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor

abstract class AbstractBackend extends Backend {
  protected def pageDescriptors: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage[BackendConfigStore]]]

  /** Global application config, its subpath serves as template for download-specific configs */
  protected def globalCfg: IGlobalConfigStore

  /** Initialize default config for a new entry */
  protected def defaultCfg: BackendConfigStore = {
    BackendConfigStore(globalCfg, settingsAccessChecker)
  }

  override def create(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String,
    cfgOption:      Option[BackendConfigStore]
  ): DownloadEntry = {
    require(isSupported(uri), "URI not supported")
    DownloadEntry(id, uri, location, filenameOption, checksumOption, comment, cfgOption getOrElse defaultCfg)
  }

  override def layoutConfig(cfgOption: Option[BackendConfigStore], tabFolder: TabFolder, isEditable: Boolean): BackendConfigUi = {
    val resultCfg = BackendConfigStore(globalCfg, settingsAccessChecker)
    new BackendConfigUiImpl(resultCfg, isEditable, cfgOption, globalCfg, tabFolder, pageDescriptors)
  }
}
