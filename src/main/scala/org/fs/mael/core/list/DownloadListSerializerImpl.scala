package org.fs.mael.core.list

import java.io.File

import org.fs.mael.core.Status
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.entry.BackendSpecificEntryData
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.json4s._
import org.json4s.ext.JavaTypesSerializers
import org.json4s.jackson.Serialization

import com.github.nscala_time.time.Imports._

class DownloadListSerializerImpl(backendMgr: BackendManager) extends DownloadListSerializer {

  private implicit val formats = {
    val deSerializer = {
      import FieldSerializer._
      FieldSerializer[DownloadEntry[_]](
        serializer   = ignore("hashCode") orElse ignore("speedOption"),
        deserializer = {
          // Coerce erased sections type from BigInt to Long
          case JField("sections", x: JObject) if !x.obj.isEmpty =>
            JField("sections", JObject(x.obj.map {
              case JField(k, JInt(v)) => JField(k, JLong(v.toLong))
            }: _*))
        }
      )
    }
    import DownloadListSerializerImpl._
    val serializers: Seq[Serializer[_]] = Seq(
      DateSerializer,
      FileSerializer,
      StatusSerializer,
      LogTypeSerializer,
      new BackendDataSerializer(backendMgr)
    )
    Serialization.formats(NoTypeHints) + deSerializer ++ serializers + SectionsKeySerializer ++ JavaTypesSerializers.all
  }

  def serialize(entries: Iterable[DownloadEntry[_]]): String = {
    Serialization.writePretty(entries).replaceAllLiterally("\r\n", "\n")
  }

  def deserialize(entriesString: String): Seq[DownloadEntry[_ <: BackendSpecificEntryData]] = {
    Serialization.read[Seq[DownloadEntry[_ <: BackendSpecificEntryData]]](entriesString)
  }
}

object DownloadListSerializerImpl {
  object DateSerializer extends CustomSerializer[DateTime](format => (
    {
      case JInt(timestamp)  => new DateTime(timestamp.toLong)
      case JLong(timestamp) => new DateTime(timestamp)
    }, {
      case dt: DateTime => JLong(dt.getMillis)
    }
  ))

  object FileSerializer extends CustomSerializer[File](format => (
    {
      case JString(pathString) => new File(pathString)
    }, {
      case f: File => JString(f.getAbsolutePath)
    }
  ))

  object StatusSerializer extends CustomSerializer[Status](format => (
    {
      case JString("Running")  => Status.Running
      case JString("Stopped")  => Status.Stopped
      case JString("Error")    => Status.Error
      case JString("Complete") => Status.Complete
    }, {
      case s: Status => JString(s.toString)
    }
  ))

  object LogTypeSerializer extends CustomSerializer[LogEntry.Type](format => (
    {
      case JString("Info")     => LogEntry.Info
      case JString("Request")  => LogEntry.Request
      case JString("Response") => LogEntry.Response
      case JString("Error")    => LogEntry.Error
    }, {
      case tpe: LogEntry.Type => JString(tpe.toString)
    }
  ))

  class BackendDataSerializer(backendMgr: BackendManager)
    extends CustomSerializer[BackendSpecificEntryData](format => (
      {
        case x: JObject =>
          implicit val formats = org.json4s.DefaultFormats
          val backendId = (x \\ "backendId").extract[String]
          val backend = backendMgr(backendId)
          backend.dataSerializer.deserialize(x)
      }, {
        case bsed: BackendSpecificEntryData =>
          val backend = backendMgr(bsed.backendId)
          backend.dataSerializer.serialize(bsed.asInstanceOf[backend.BSED])
      }
    ))

  /** Dirty hack for serialization of sections whose runtime class is {@code mutable.Map[Object, Object]} */
  object SectionsKeySerializer extends CustomKeySerializer[Object](format => (
    {
      case intKeyStr =>
        new java.lang.Long(intKeyStr.toLong)
    }, {
      case key: Long =>
        key.toString
    }
  ))
}
