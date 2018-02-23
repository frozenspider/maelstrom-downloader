package org.fs.mael.ui

object ConfigOptions {
  import scala.language.implicitConversions

  sealed trait OptionIdTrait
  type Id = String with OptionIdTrait

  val DownloadPath: Id = "main.downloadPath"
  val NetworkTimeout: Id = "main.networkTimeout"

  private implicit def string2optnId(s: String): Id = s.asInstanceOf[Id]
}
