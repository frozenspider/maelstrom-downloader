package org.fs.mael.core.config

import java.io.File

class GlobalConfigStore(file: File)
  extends FileBackedConfigStore(file)
  with IGlobalConfigStore {

}
