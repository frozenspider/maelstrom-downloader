package org.fs.mael.ui

object ConfigOptions {
  sealed trait ConfigOption[T] { def id: String }
  case class SimpleConfigOption[T](id: String) extends ConfigOption[T]
  abstract class CustomConfigOption[T, Repr](val id: String) extends ConfigOption[T] {
    def asReprOption: SimpleConfigOption[Repr] = SimpleConfigOption(id)
    def toRepr(v: T): Repr
    def fromRepr(v: Repr): T
  }

  val DownloadPath: SimpleConfigOption[String] = SimpleConfigOption("main.downloadPath")
  val NetworkTimeout: SimpleConfigOption[Int] = SimpleConfigOption("main.networkTimeoutSeconds")
  val ActionOnWindowClose: CustomConfigOption[OnWindowClose, String] = new CustomConfigOption[OnWindowClose, String]("main.actionOnWindowClose") {
    def toRepr(v: OnWindowClose): String = v.id
    def fromRepr(v: String): OnWindowClose = OnWindowClose.values.find(_.id == v).get
  }

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
}
