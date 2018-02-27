package org.fs.mael.backend

import java.io.File
import java.net.URI

import org.fs.mael.core.backend.Backend
import org.fs.mael.core.backend.BackendDataSerializer
import org.fs.mael.core.backend.BackendDownloader
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry

class StubBackend extends Backend {
  override type BSED = StubBackend.StubEntryData

  override val dataClass: Class[BSED] = classOf[StubBackend.StubEntryData]

  override val id: String = StubBackend.Id

  override def isSupported(uri: URI): Boolean = true

  override val downloader: BackendDownloader[BSED] = new BackendDownloader[BSED] {
    def startInner(de: DownloadEntry[BSED], timeoutSec: Int): Unit = { println("started " + de) }
    def stopInner(de: DownloadEntry[BSED]): Unit = { println("stopped " + de) }
  }

  override val dataSerializer: BackendDataSerializer[BSED] = StubBackend.StubDataSerializer

  override protected def createInner(
    uri:            URI,
    location:       File,
    filenameOption: Option[String],
    comment:        String
  ): DownloadEntry[BSED] = {
    DownloadEntry(id, uri, location, filenameOption, comment, new StubBackend.StubEntryData)
  }
}

object StubBackend {
  val Id: String = "dummy"

  import org.json4s._
  import org.json4s.jackson.Serialization

  implicit private val formats = Serialization.formats(NoTypeHints)

  class StubEntryData extends BackendSpecificEntryData {
    override val backendId = Id
    var myParam: String = ""

    override def equalsInner(that: BackendSpecificEntryData): Boolean = that match {
      case that: StubEntryData => this.myParam == that.myParam
      case _                   => false
    }

    override def hashCodeInner: Int = 31 * myParam.hashCode
  }

  object StubDataSerializer extends BackendDataSerializer[StubEntryData] {
    override def serializeFields(ed: StubEntryData): Seq[JField] = {
      Seq(
        JField("myParam", JString(ed.myParam))
      )
    }

    override def deserialize(jObj: JObject): StubEntryData = {
      val ed = new StubEntryData
      ed.myParam = (jObj \ "myParam").extract[String]
      ed
    }
  }
}
