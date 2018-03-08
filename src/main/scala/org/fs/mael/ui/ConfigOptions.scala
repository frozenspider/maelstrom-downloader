package org.fs.mael.ui

object ConfigOptions {
  sealed trait ConfigOption[T] { def id: String }
  case class SimpleConfigOption[T](id: String) extends ConfigOption[T]
  abstract class CustomConfigOption[T, Repr](val id: String) extends ConfigOption[T] {
    def asReprOption: SimpleConfigOption[Repr] = SimpleConfigOption(id)
    def toRepr(v: T): Repr
    def fromRepr(v: Repr): T
  }
  class RadioConfigOption[T <: RadioOption](id: String, values: Seq[T]) extends CustomConfigOption[T, String](id) {
    def toRepr(v: T): String = v.id
    def fromRepr(v: String): T = values.find(_.id == v).get
  }

  val DownloadPath: SimpleConfigOption[String] = SimpleConfigOption("main.downloadPath")
  val NetworkTimeout: SimpleConfigOption[Int] = SimpleConfigOption("main.networkTimeoutMs")
  val SortColumn: SimpleConfigOption[String] = SimpleConfigOption("view.sortColumn")
  val SortAsc: SimpleConfigOption[Boolean] = SimpleConfigOption("view.sortAsc")
  val ActionOnWindowClose: RadioConfigOption[OnWindowClose] =
    new RadioConfigOption("main.actionOnWindowClose", OnWindowClose.values)
  val MinimizeToTrayBehaviour: RadioConfigOption[MinimizeToTray] =
    new RadioConfigOption("main.minimizeToTrayBehaviour", MinimizeToTray.values)

  sealed abstract class RadioOption(val id: String, val prettyName: String) {
    override def toString = s"('$prettyName', id=$id)"
  }

  sealed abstract class OnWindowClose(id: String, prettyName: String) extends RadioOption(id, prettyName)
  object OnWindowClose {
    object Undefined extends OnWindowClose("", "Prompt")
    object Close extends OnWindowClose("CLOSE", "Close")
    object Minimize extends OnWindowClose("MINIMIZE", "Minimize")
    val values = Seq(Undefined, Close, Minimize)
  }

  sealed abstract class MinimizeToTray(id: String, prettyName: String) extends RadioOption(id, prettyName)
  object MinimizeToTray {
    object Never extends MinimizeToTray("NEVER", "Never")
    object OnClose extends MinimizeToTray("ON_CLOSE", "On window close")
    object Always extends MinimizeToTray("ALWAYS", "Always")
    val values = Seq(Never, OnClose, Always)
  }
}
