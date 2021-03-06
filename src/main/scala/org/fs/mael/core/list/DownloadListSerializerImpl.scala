package org.fs.mael.core.list

import java.io.File

import org.fs.mael.core.Status
import org.fs.mael.core.backend.BackendManager
import org.fs.mael.core.checksum.ChecksumType
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.json4s._
import org.json4s.ext.JavaTypesSerializers
import org.json4s.jackson.Serialization

import com.github.nscala_time.time.Imports._

class DownloadListSerializerImpl(
  globalCfg:  IGlobalConfigStore,
  backendMgr: BackendManager
) extends DownloadListSerializer {

  private implicit val formats = {
    val deSerializer = {
      import FieldSerializer._
      FieldSerializer[DownloadEntry](
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
      new BackendConfigSerializer(globalCfg, backendMgr),
      new EnumSerializer[ChecksumType]
    )
    Serialization.formats(NoTypeHints) + deSerializer ++ serializers + SectionsKeySerializer ++ JavaTypesSerializers.all
  }

  def serialize(entries: Iterable[DownloadEntry]): String = {
    Serialization.writePretty(entries).replaceAllLiterally("\r\n", "\n")
  }

  def deserialize(entriesString: String): Seq[DownloadEntry] = {
    Serialization.read[Seq[DownloadEntry]](entriesString)
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
      case JString(pathString) =>
        new File(pathString)
    }, {
      case f: File =>
        JString(f.getAbsolutePath)
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

  class BackendConfigSerializer(globalCfg: IGlobalConfigStore, backendMgr: BackendManager)
    extends CustomSerializer[BackendConfigStore](format => (
      {
        case JString(s) if s matches "[a-zA-Z-]\\|" =>
          val backendId = s.dropRight(1)
          val accessChecker = backendMgr(backendId).settingsAccessChecker
          BackendConfigStore(globalCfg, accessChecker)
        case JString(s) =>
          val Array(backendId, serialString) = s.split("\\|", 2)
          val accessChecker = backendMgr(backendId).settingsAccessChecker
          BackendConfigStore(serialString, globalCfg, accessChecker)
      }, {
        case cfg: BackendConfigStore =>
          JString(cfg.toSerialForm.productIterator.mkString("|"))
      }
    ))

  class EnumSerializer[E <: Enum[E]](implicit ct: Manifest[E]) extends CustomSerializer[E](format => (
    {
      case JString(name) =>
        Enum.valueOf(ct.runtimeClass.asInstanceOf[Class[E]], name)
    }, {
      case dt: E =>
        JString(dt.name)
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
