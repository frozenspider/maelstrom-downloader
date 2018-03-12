package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.InMemoryConfigManager
import org.fs.mael.core.entry.DownloadEntry

/*
 * Backend needs to know
 * - backend-specific download properties
 * UI needs to query backend for
 * - backend-specific UI
 */
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
  ): DownloadEntry = {
    require(isSupported(uri), "URI not supported")
    createInner(uri, location, filenameOption, checksumOption, comment, cfgOption getOrElse defaultCfg)
  }

  def downloader: BackendDownloader

  def layoutConfig(cfgOption: Option[InMemoryConfigManager], tabFolder: TabFolder, isEditable: Boolean): BackendConfigUi

  protected def defaultCfg: InMemoryConfigManager

  protected def createInner(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String,
    cfg:            InMemoryConfigManager
  ): DownloadEntry = {
    DownloadEntry(id, uri, location, filenameOption, checksumOption, comment, cfg)
  }
}
