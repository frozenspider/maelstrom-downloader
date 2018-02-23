package org.fs.mael.ui

object ConfigOptions {
  val DownloadPath: ConfigOption[String] = ConfigOption("main.downloadPath")
  val NetworkTimeout: ConfigOption[Int] = ConfigOption("main.networkTimeoutSeconds")

  case class ConfigOption[T](id: String)
}
