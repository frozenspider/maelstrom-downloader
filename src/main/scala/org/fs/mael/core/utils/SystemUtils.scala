package org.fs.mael.core.utils

import java.awt.Desktop
import java.io.File

import scala.util.control.NonFatal

import org.apache.commons.lang3.{SystemUtils => ApacheSystemUtils}
import org.fs.mael.core.utils.CoreUtils._
import org.fs.utility.Imports._
import org.slf4s.Logging

trait SystemUtils extends Logging {
  def openFolders(foldersWithFiles: Seq[(File, Option[String])]): Unit = {
    foldersWithFiles map (_._1) foreach (requireFolderExist)
    for {
      (folder, fileOptions) <- foldersWithFiles.groupBy(_._1).mapValues(_.map(_._2))
      files = fileOptions.yieldDefined filter (new File(folder, _).exists)
    } openFolder(folder, files)
  }

  def openFolder(folder: File, filenames: Seq[String]): Unit = {
    requireFolderExist(folder)
    if (ApacheSystemUtils.IS_OS_WINDOWS && filenames.nonEmpty) {
      try {
        filenames foreach { filename =>
          val fullPath = new File(folder, filename).getAbsolutePath
          Runtime.getRuntime.exec(s"""explorer.exe /select, "${fullPath}"""")
        }
      } catch {
        case NonFatal(ex) =>
          log.warn("Failed to open folder in Windows explorer", ex)
          openFolderFallback(folder)
      }
    } else {
      openFolderFallback(folder)
    }
  }

  private def openFolderFallback(folder: File): Unit = {
    requireFolderExist(folder)
    Desktop.getDesktop.open(folder)
  }

  def requireFolderExist(folder: File): Unit = {
    requireFriendly(folder.exists, s"Folder does not exist: $folder")
    requireFriendly(folder.isDirectory, s"Path is not a directory: $folder")
  }
}

object SystemUtils extends SystemUtils
