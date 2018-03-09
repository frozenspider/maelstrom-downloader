package org.fs.mael.test.stub

import java.net.URI

import org.fs.mael.core.backend.BackendDataSerializer
import org.fs.mael.core.entry.BackendSpecificEntryData

class StubBackend
  extends AbstractSimpleBackend[StubBackend.StubEntryData](
    StubBackend.Id
  ) {
  override def isSupported(uri: URI): Boolean = true
  override val dataSerializer: BackendDataSerializer[StubBackend.StubEntryData] =
    StubBackend.StubDataSerializer
  override val defaultData = new StubBackend.StubEntryData
}

object StubBackend {
  val Id: String = "dummy"

  import org.json4s._
  import org.json4s.jackson.Serialization

  implicit private val formats = Serialization.formats(NoTypeHints)

  class StubEntryData extends BackendSpecificEntryData {
    override val backendId = Id
    var myParam: String = ""

    def this(myParam: String) = {
      this()
      this.myParam = myParam
    }

    override def equalsInner(that: BackendSpecificEntryData): Boolean = that match {
      case that: StubEntryData => this.myParam == that.myParam
      case _                   => false
    }

    override def hashCodeInner: Int = 31 * myParam.hashCode
  }

  object StubDataSerializer extends BackendDataSerializer[StubEntryData] {
    override def serializeFields(ed: StubEntryData): Seq[JField] = {
      Seq(JField("myParam", JString(ed.myParam)))
    }

    override def deserialize(jObj: JObject): StubEntryData = {
      new StubEntryData((jObj \ "myParam").extract[String])
    }
  }
}
