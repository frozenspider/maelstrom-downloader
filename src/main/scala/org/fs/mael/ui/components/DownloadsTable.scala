package org.fs.mael.ui.components

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.widgets._
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._

import com.github.nscala_time.time.Imports._

class DownloadsTable(
  parent:    Composite,
  resources: Resources
) extends MUiComponent[Table](parent) {

  private val columnDefs: IndexedSeq[DownloadsTable.DownloadColumnDef] = {
    import DownloadsTable.{ DownloadColumnDef => CD, _ => _ }
    IndexedSeq(
      CD("File Name", de => de.displayName),
      CD("%", downloadEntityFormat.downloadedPercent, 45, false),
      CD("Downloaded", downloadEntityFormat.downloadedSize),
      CD("Size", downloadEntityFormat.size, 80),
      CD("Comment", de => de.comment, 200),
      CD("Added", de => de.dateCreated.toString(resources.dateTimeFmt), 120)
    )
  }

  override val peer: Table = {
    // TODO: Make table sortable
    val table = new Table(parent, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION).withCode { table =>
      table.setLinesVisible(true)
      table.setHeaderVisible(true)
    }

    columnDefs.foreach { cd =>
      val c = new TableColumn(table, SWT.NONE)
      c.setText(cd.name)
      c.setWidth(cd.width)
      c.setResizable(cd.resizable)
    }

    installDefaultHotkeys(table)
    table
  }

  /** Return all selected entries */
  def selectedEntries: Seq[DownloadEntryView] = {
    peer.getSelection map (_.getData match {
      case de: DownloadEntryView => de
    })
  }

  /** Return a singular selected entry. If multiple entries are selected, returns {@code None}. */
  def selectedEntryOption: Option[DownloadEntryView] = {
    if (peer.getSelectionCount == 1) {
      selectedEntries.headOption
    } else {
      None
    }
  }

  def indexOfOption(de: DownloadEntryView): Option[Int] = {
    peer.getItems.indexWhere(_.getData match {
      case de2: DownloadEntryView if de2.id == de.id => true
      case _                                         => false
    }) match {
      case -1 => None
      case x  => Some(x)
    }
  }

  def init(entries: Iterable[DownloadEntryView]): Unit = {
    require(peer.getItemCount == 0, "Table isn't empty")
    val sorted = entries.toSeq.sortBy(_.dateCreated)
    sorted.foreach { de =>
      val newRow = new TableItem(peer, SWT.NONE)
      fillRow(newRow, de)
    }
    adjustColumnWidths()
  }

  def add(de: DownloadEntryView): Unit = {
    val newRow = new TableItem(peer, SWT.NONE)
    fillRow(newRow, de)
    peer.deselectAll()
    peer.showItem(newRow)
    peer.select(peer.getItems.indexOf(newRow)) // Why no event fired?
  }

  def remove(de: DownloadEntryView): Unit = {
    indexOfOption(de) map (peer.remove)
  }

  def update(de: DownloadEntryView): Unit = {
    indexOfOption(de) map (peer.getItem) map (row => fillRow(row, de))
  }

  private def fillRow(row: TableItem, de: DownloadEntryView): Unit = {
    row.setData(de)
    row.setImage(0, resources.icon(de.status))
    columnDefs.zipWithIndex.foreach {
      case (cd, i) => row.setText(i, cd.fmt(de))
    }
    if (de.supportsResumingOption == Some(false)) {
      // Would be better to add red-ish border, but that's non-trivial
      row.setForeground(new Color(display, 0x80, 0x00, 0x00))
    }
  }

  private def adjustColumnWidths(): Unit = {
    peer.getColumns.filter(_.getWidth == 0).map(_.pack())
  }
}

object DownloadsTable {
  private case class DownloadColumnDef(
    name:      String,
    fmt:       DownloadEntryView => String,
    width:     Int                         = 0,
    resizable: Boolean                     = true
  )()

  private object downloadEntityFormat {
    def size(de: DownloadEntryView): String = {
      de.sizeOption map fmtSizePretty getOrElse ""
    }

    def downloadedSize(de: DownloadEntryView): String = {
      if (!de.sections.isEmpty) {
        fmtSizePretty(de.downloadedSize)
      } else {
        ""
      }
    }

    def downloadedPercent(de: DownloadEntryView): String = {
      val downloadedSize = de.downloadedSize
      de.sizeOption match {
        case Some(totalSize) =>
          val percent = downloadedSize * 100 / totalSize
          percent + "%"
        case _ =>
          ""
      }
    }

    private def fmtSizePretty(size: Long): String = {
      val groups = size.toString.reverse.grouped(3).map(_.reverse).toSeq.reverse
      groups.mkString("", " ", " B")
    }
  }
}
