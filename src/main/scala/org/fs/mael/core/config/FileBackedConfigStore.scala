package org.fs.mael.core.config

import java.io.File
import java.io.FileNotFoundException

import org.eclipse.jface.preference.PreferenceStore
import org.fs.mael.core.utils.CoreUtils._

class FileBackedConfigStore(val file: File) extends IConfigStoreImpl {
  override lazy val inner = new PreferenceStore().withCode { store =>
    file.getParentFile.mkdirs()
    store.setFilename(file.getAbsolutePath)
    try {
      store.load()
    } catch {
      case ex: FileNotFoundException => // NOOP
    }
  }

  def save(): Unit = {
    inner.save()
  }
}
