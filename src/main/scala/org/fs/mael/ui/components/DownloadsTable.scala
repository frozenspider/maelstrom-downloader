package org.fs.mael.ui.components

import java.text.Collator
import java.util.Locale

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.widgets._
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.ConfigManager
import org.fs.mael.ui.ConfigOptions
import org.fs.mael.ui.components.DownloadsTable._
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._

import com.github.nscala_time.time.Imports._

class DownloadsTable(
  parent:    Composite,
  resources: Resources,
  cfgMgr:    ConfigManager
) extends MUiComponent[Table](parent) {

  private val columnDefs: IndexedSeq[DownloadsTable.ColumnDef] = {
    IndexedSeq(
      ColumnDef("file-name", "File Name", de => de.displayName),
      ColumnDef("dl-percent", "%", downloadEntityFormat.downloadedPercent, 45, false),
      ColumnDef("dl-value", "Downloaded", downloadEntityFormat.downloadedSize),
      ColumnDef("file-size", "Size", downloadEntityFormat.size, 80),
      ColumnDef("comment", "Comment", de => de.comment, 200),
      ColumnDef("date-created", "Added", de => de.dateCreated.toString(resources.dateTimeFmt), 120)
    )
  }

  override val peer: Table = {
    val table = new Table(parent, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION).withCode { table =>
      table.setLinesVisible(true)
      table.setHeaderVisible(true)
    }

    columnDefs.foreach { cd =>
      val c = new TableColumn(table, SWT.NONE)
      c.setData(cd)
      c.setText(cd.name)
      c.setWidth(cd.width)
      c.setResizable(cd.resizable)
      c.addListener(SWT.Selection, sortListener)
    }

    installDefaultHotkeys(table)
    table
  }

  loadSorting()

  /** Return all selected entries */
  def selectedEntries: Seq[DownloadEntryView] = {
    peer.getSelection map (_.de)
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
    peer.getItems.indexWhere(_.de match {
      case de2 if de2.id == de.id => true
      case _                      => false
    }) match {
      case -1 => None
      case x  => Some(x)
    }
  }

  def init(entries: Iterable[DownloadEntryView]): Unit = {
    require(peer.getItemCount == 0, "Table isn't empty")
    val sorted = entries.toSeq.sortBy(_.dateCreated)
    require(sorted.map(_.dateCreated).distinct == sorted.map(_.dateCreated), "Download list contains entries with the same created date")
    sorted.foreach { de =>
      val newRow = new TableItem(peer, SWT.NONE)
      fillRow(newRow, de)
    }
    adjustColumnWidths()
    sortContent()
    fireSelectionUpdated()
  }

  def add(de: DownloadEntryView): Unit = {
    val newRow = new TableItem(peer, SWT.NONE)
    fillRow(newRow, de)
    peer.deselectAll()
    peer.showItem(newRow)
    peer.select(peer.getItems.indexOf(newRow))
    sortContent()
    fireSelectionUpdated()
  }

  def remove(de: DownloadEntryView): Unit = {
    indexOfOption(de) foreach { idx =>
      peer.remove(idx)
      fireSelectionUpdated()
    }
  }

  def update(de: DownloadEntryView): Unit = {
    indexOfOption(de) match {
      case Some(idx) => fillRow(peer.getItem(idx), de)
      case None      => // NOOP
    }
  }

  private def fireSelectionUpdated(): Unit = {
    // We could fill the event properly, but our code doesn't care about event details
    peer.notifyListeners(SWT.Selection, new Event())
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

  /** Sort table content by the given column in the given direction */
  private def sortContent(column: TableColumn, asc: Boolean): Unit = {
    peer.setSortColumn(column)
    peer.setSortDirection(if (asc) SWT.UP else SWT.DOWN)
    sortContent()
  }

  /** Sort table content according to currently set sort column and direction */
  private def sortContent(): Unit = {
    val oldSelectedData = peer.getSelection.map(_.de).toSet
    val asc = peer.getSortDirection == SWT.UP
    val colIdx = peer.getColumns.indexOf(peer.getSortColumn)
    val collator = Collator.getInstance(Locale.getDefault)
    val items = peer.getItems
    // Selection sort
    for (i <- 0 until (items.length - 1)) {
      var minIdx = i
      var minValue = items(minIdx).getText(colIdx)
      for (j <- (i + 1) until items.length) {
        val value = items(j).getText(colIdx)
        val shouldSwap: Boolean = {
          val cmp = collator.compare(minValue, value) match {
            case 0 =>
              // Equal elements are compared by date added
              items(minIdx).de.dateCreated compare items(j).de.dateCreated
            case x => x
          }
          assert(cmp != 0)
          (asc && cmp > 0) || (!asc && cmp < 0)
        }
        if (shouldSwap) {
          minIdx = j
          minValue = value
        }
      }
      if (minIdx != i) {
        // Swap rows
        val de1 = items(i).de
        val de2 = items(minIdx).de
        fillRow(items(i), de2)
        fillRow(items(minIdx), de1)
      }
    }
    peer.setSortColumn(peer.getColumn(colIdx))
    peer.setSortDirection(if (asc) SWT.UP else SWT.DOWN)
    val newSelectedData = peer.getItems filter (item => oldSelectedData.contains(item.de))
    peer.setSelection(newSelectedData)
  }

  private lazy val sortListener: Listener = e => {
    val column = e.widget.asInstanceOf[TableColumn]
    val asc =
      if (peer.getSortColumn == column) {
        peer.getSortDirection match {
          case SWT.UP   => false
          case SWT.DOWN => true
        }
      } else {
        true
      }
    sortContent(column, asc)
    cfgMgr.setProperty(ConfigOptions.SortColumn, column.columnDef.id)
    cfgMgr.setProperty(ConfigOptions.SortAsc, asc)
  }

  private def loadSorting() = {
    val colId = cfgMgr.getProperty(ConfigOptions.SortColumn)
    val asc = cfgMgr.getProperty(ConfigOptions.SortAsc)
    val colOption = peer.getColumns.find(_.columnDef.id == colId)
    colOption foreach { col =>
      sortContent(col, asc)
    }
  }

  private implicit class RichTableItem(ti: TableItem) {
    def de: DownloadEntryView = {
      ti.getData.asInstanceOf[DownloadEntryView]
    }
  }

  private implicit class RichTableColumn(tc: TableColumn) {
    def columnDef: DownloadsTable.ColumnDef = {
      tc.getData.asInstanceOf[DownloadsTable.ColumnDef]
    }
  }
}

object DownloadsTable {
  private case class ColumnDef(
    id:        String, // Used to uniquely identify column
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
