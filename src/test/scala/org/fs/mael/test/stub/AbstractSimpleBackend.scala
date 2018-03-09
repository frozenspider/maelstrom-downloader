package org.fs.mael.test.stub

import java.io.File
import java.net.URI

import scala.reflect.ClassTag

import org.eclipse.swt.widgets.TabFolder
import org.fs.mael.core.backend.Backend
import org.fs.mael.core.backend.BackendConfigUi
import org.fs.mael.core.backend.BackendDataSerializer
import org.fs.mael.core.backend.BackendDownloader
import org.fs.mael.core.checksum.Checksum
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry

abstract class AbstractSimpleBackend[T <: BackendSpecificEntryData: ClassTag](
  override val id: String,
  val emptyBsed:   T
) extends Backend {
  override type BSED = T

  override def dataClass: Class[BSED] = {
    val ct = implicitly[ClassTag[BSED]]
    ct.runtimeClass.asInstanceOf[Class[BSED]]
  }

  override def dataSerializer: BackendDataSerializer[BSED] = new StubDataSerializer(emptyBsed)

  override def downloader: BackendDownloader[BSED] = new BackendDownloader[BSED](id) {
    override def eventMgr = ???
    override def transferMgr = ???
    def startInner(de: DownloadEntry[BSED], timeoutSec: Int): Unit = downloadStarted(de, timeoutSec)
    def stopInner(de: DownloadEntry[BSED]): Unit = downloadStopped(de)
  }

  def downloadStarted(de: DownloadEntry[BSED], timeoutSec: Int): Unit = {}

  def downloadStopped(de: DownloadEntry[BSED]): Unit = {}

  override protected def createInner(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    checksumOption: Option[Checksum],
    comment:        String
  ): DownloadEntry[BSED] = {
    DownloadEntry[BSED](id, uri, location, filenameOption, checksumOption, comment, emptyBsed)
  }

  override def layoutConfig(tabFolder: TabFolder, cfgMgr: ConfigManager) = new BackendConfigUi[T] {
    override def get(): BSED = emptyBsed
  }
}
