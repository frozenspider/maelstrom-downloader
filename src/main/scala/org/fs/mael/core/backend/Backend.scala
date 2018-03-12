package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.entry.DownloadEntry

trait Backend {
  val id: String

  def isSupported(uri: URI): Boolean

  /** Create a {@code DownloadEntry} from an URI */
  def create(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String,
    cfgOption:      Option[InMemoryConfigManager]
  ): DownloadEntry

  def downloader: BackendDownloader

  def layoutConfig(cfgOption: Option[InMemoryConfigManager], tabFolder: TabFolder, isEditable: Boolean): BackendConfigUi
}
