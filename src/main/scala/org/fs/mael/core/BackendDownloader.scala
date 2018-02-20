package org.fs.mael.core

trait BackendDownloader[DE] {
  def start(de: DE): Unit

  def stop(de: DE): Unit
}
