package org.fs.mael.backend.http

import scala.collection.Seq

import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.StringFieldEditor
import org.fs.mael.ui.config.EmptyPreferencePage
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor

object HttpSettings {
  import org.fs.mael.core.config.ConfigSetting

  private val prefix = HttpBackend.Id

  object Global {
    private val rootPageDescriptor =
      MPreferencePageDescriptor("HTTP", None, classOf[EmptyPreferencePage])

    val pageDescriptors: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]] = Seq(
      rootPageDescriptor,
      MPreferencePageDescriptor("Headers", Some(rootPageDescriptor.name), classOf[HeadersPage])
    )
  }

  object Local {
    val pageDescriptors: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage]] = Seq(
      MPreferencePageDescriptor("Headers", None, classOf[HeadersPage])
    )
  }

  val UserAgent: ConfigSetting[Option[String]] =
    ConfigSetting(prefix + ".userAgent", None)

  private class HeadersPage extends MFieldEditorPreferencePage(FieldEditorPreferencePage.FLAT) {
    override def createFieldEditors(): Unit = {
      row(UserAgent) { (setting, parent) =>
        new StringFieldEditor(setting.id, "User-Agent:", parent)
      }
    }
  }
}
