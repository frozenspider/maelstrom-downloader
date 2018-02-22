package org.fs.mael.ui

object ConfigOptions {
  sealed trait OptionIdTrait
  type Id = String with OptionIdTrait

  val DownloadPath: Id = "main.downloadPath"

  private implicit def string2optnId(s: String): Id = s.asInstanceOf[Id]
}
