package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.backend.ui.BackendConfigUi
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.SettingsAccessChecker
import org.fs.mael.core.entry.DownloadEntry

trait Backend {
  val id: String

  /** Initialize settings. Repeated calls should be safe. */
  def init(): Unit

  def isSupported(uri: URI): Boolean

  /** Create a {@code DownloadEntry} from an URI */
  def create(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String,
    cfgOption:      Option[BackendConfigStore]
  ): DownloadEntry

  def downloader: BackendDownloader

  def settingsAccessChecker: SettingsAccessChecker

  def layoutConfig(cfgOption: Option[BackendConfigStore], tabFolder: TabFolder, isEditable: Boolean): BackendConfigUi
}
