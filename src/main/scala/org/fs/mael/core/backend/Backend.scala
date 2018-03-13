package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.backend.ui.BackendConfigUi
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.InMemoryConfigStore
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
    cfgOption:      Option[InMemoryConfigStore]
  ): DownloadEntry

  def downloader: BackendDownloader

  def layoutConfig(cfgOption: Option[InMemoryConfigStore], tabFolder: TabFolder, isEditable: Boolean): BackendConfigUi
}
