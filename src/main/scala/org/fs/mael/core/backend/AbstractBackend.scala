package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.backend.ui.BackendConfigUi
import org.fs.mael.core.backend.ui.BackendConfigUiImpl
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.ConfigStore
import org.fs.mael.core.config.InMemoryConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor

abstract class AbstractBackend extends Backend {
  protected def pageDescriptors: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]]

  /** Global application config, its subpath serves as template for download-specific configs */
  protected def globalCfg: ConfigStore

  /** Initialize default config for a new entry */
  protected def defaultCfg: InMemoryConfigStore = {
    new InMemoryConfigStore(globalCfg, id)
  }

  override def create(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String,
    cfgOption:      Option[InMemoryConfigStore]
  ): DownloadEntry = {
    require(isSupported(uri), "URI not supported")
    DownloadEntry(id, uri, location, filenameOption, checksumOption, comment, cfgOption getOrElse defaultCfg)
  }

  override def layoutConfig(cfgOption: Option[InMemoryConfigStore], tabFolder: TabFolder, isEditable: Boolean): BackendConfigUi = {
    new BackendConfigUiImpl(id, isEditable, cfgOption, globalCfg, tabFolder, pageDescriptors)
  }
}
