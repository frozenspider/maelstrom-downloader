package org.fs.mael.core.backend

import java.io.File
import java.net.URI

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry

/*
 * Backend needs to know
 * - backend-specific download properties
 * UI needs to query backend for
 * - backend-specific UI
 */
trait Backend {
  type BSED <: BackendSpecificEntryData

  def dataClass: Class[BSED]

  val id: String

  def isSupported(uri: URI): Boolean

  /** Create a {@code DownloadEntry} from an URI */
  def create(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String,
    dataOption:     Option[BSED]
  ): DownloadEntry[BSED] = {
    require(isSupported(uri), "URI not supported")
    createInner(uri, location, filenameOption, checksumOption, comment, dataOption getOrElse defaultData)
  }

  def downloader: BackendDownloader[BSED]

  def dataSerializer: BackendDataSerializer[BSED]

  def layoutConfig(dataOption: Option[BSED], tabFolder: TabFolder): BackendConfigUi[BSED]

  protected def defaultData: BSED

  protected def createInner(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String,
    data:           BSED
  ): DownloadEntry[BSED] = {
    DownloadEntry[BSED](id, uri, location, filenameOption, checksumOption, comment, data)
  }
}
