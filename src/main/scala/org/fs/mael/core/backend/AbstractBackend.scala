package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor

abstract class AbstractBackend extends Backend {
  protected def pageDescriptors: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]]

  protected def globalCfgMgr: ConfigManager

  /** Initialize default config for a new entry */
  protected def defaultCfg: InMemoryConfigManager = {
    new InMemoryConfigManager(globalCfgMgr, id)
  }

  override def create(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String,
    cfgOption:      Option[InMemoryConfigManager]
  ): DownloadEntry = {
    require(isSupported(uri), "URI not supported")
    DownloadEntry(id, uri, location, filenameOption, checksumOption, comment, cfgOption getOrElse defaultCfg)
  }

  override def layoutConfig(cfgOption: Option[InMemoryConfigManager], tabFolder: TabFolder, isEditable: Boolean): BackendConfigUi = {
    val ui = new BackendConfigUiImpl(id, pageDescriptors, isEditable)
    ui.initialize(cfgOption, tabFolder, globalCfgMgr)
    ui
  }
}
