package org.fs.mael.backend.http.config

import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.StringFieldEditor
import org.fs.mael.backend.http.HttpBackend
import org.fs.mael.backend.http.ui._
import org.fs.mael.core.config.BackendConfigStore
import org.fs.mael.core.config.ConfigSetting.LocalEntityConfigSetting
import org.fs.mael.core.config.IGlobalConfigStore
import org.fs.mael.core.proxy.Proxy
import org.fs.mael.ui.components.proxy.ProxyLocalFieldEditor
import org.fs.mael.ui.config.EmptyPreferencePage
import org.fs.mael.ui.config.GlobalSettings
import org.fs.mael.ui.config.MFieldEditorPreferencePage
import org.fs.mael.ui.config.MPreferencePageDescriptor

object HttpSettings {
  import org.fs.mael.core.config.ConfigSetting

  private val prefix = HttpBackend.Id

  //
  // Settings
  //

  val UserAgent: ConfigSetting[Option[String]] =
    ConfigSetting(prefix + ".userAgent", None)

  val Cookies: ConfigSetting[Map[String, String]] =
    new CookiesConfigSetting(prefix + ".cookies")

  val Headers: ConfigSetting[Map[String, String]] =
    new HeadersConfigSetting(prefix + ".headers")

  val ConnectionProxy: LocalEntityConfigSetting[Proxy] =
    new LocalEntityConfigSetting[Proxy](prefix + ".proxy", GlobalSettings.ConnectionProxies, GlobalSettings.ConnectionProxy, Proxy.Classes)

  //
  // Page groups
  //

  /** Setting pages to include in global settings */
  object Global {
    private val rootPageDescriptor =
      MPreferencePageDescriptor("HTTP", None, classOf[EmptyPreferencePage[IGlobalConfigStore]])

    val pageDescriptors: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage[IGlobalConfigStore]]] = Seq(
      rootPageDescriptor,
      MPreferencePageDescriptor("Headers", Some(rootPageDescriptor.name), classOf[GlobalHeadersPage])
    )
  }

  /** Setting pages for single download */
  object Local {
    val pageDescriptors: Seq[MPreferencePageDescriptor[_ <: MFieldEditorPreferencePage[BackendConfigStore]]] = Seq(
      MPreferencePageDescriptor("Headers", None, classOf[LocalHeadersPage]),
      MPreferencePageDescriptor("Proxy", None, classOf[LocalProxyPage])
    )
  }

  //
  // Pages
  //

  private class GlobalHeadersPage extends MFieldEditorPreferencePage[IGlobalConfigStore](FieldEditorPreferencePage.FLAT) {
    override def createFieldEditors(): Unit = {
      row(UserAgent) { (setting, parent) =>
        new StringFieldEditor(setting.id, "User-Agent:", parent)
      }
      row(Headers) { (setting, parent) =>
        new HeadersFieldEditor(setting.id, "Headers:", parent)
      }
    }
  }

  private class LocalHeadersPage extends MFieldEditorPreferencePage[BackendConfigStore](FieldEditorPreferencePage.FLAT) {
    override def createFieldEditors(): Unit = {
      row(UserAgent) { (setting, parent) =>
        // TODO: Add optional dropdown with existing user-agents
        new StringFieldEditor(setting.id, "User-Agent:", parent)
      }
      row(Cookies) { (setting, parent) =>
        new CookiesFieldEditor(setting.id, "Cookies:", parent)
      }
      row(Headers) { (setting, parent) =>
        new HeadersFieldEditor(setting.id, "Headers:", parent)
      }
    }
  }

  private class LocalProxyPage extends MFieldEditorPreferencePage[BackendConfigStore](FieldEditorPreferencePage.FLAT) {
    override def createFieldEditors(): Unit = {
      customRow(ConnectionProxy) { parent =>
        new ProxyLocalFieldEditor("Proxy:", ConnectionProxy, GlobalSettings.ConnectionProxies, parent)
      }
    }
  }
}
