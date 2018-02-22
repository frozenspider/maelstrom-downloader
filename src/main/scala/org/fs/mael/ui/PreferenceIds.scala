package org.fs.mael.ui

object PreferenceIds {
  sealed trait PreferenceIdTrait
  type PreferenceId = String with PreferenceIdTrait

  val DownloadPath: PreferenceId = "main.downloadPath"

  private implicit def string2prefId(s: String): PreferenceId = s.asInstanceOf[PreferenceId]
}
