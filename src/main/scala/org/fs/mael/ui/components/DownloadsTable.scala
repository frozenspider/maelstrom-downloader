package org.fs.mael.ui.components

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.TableColumn
import org.eclipse.swt.widgets.TableItem
import org.fs.mael.core.config.ConfigManager
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.components.DownloadsTable._
import org.fs.mael.ui.prefs.GlobalPreferences
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._

import com.github.nscala_time.time.Imports._

class DownloadsTable(
  parent:    Composite,
  resources: Resources,
  cfgMgr:    ConfigManager
) extends MUiComponent[Table](parent) {

  private val columnDefs: IndexedSeq[ColumnDef[_]] = {
    IndexedSeq(
      ColumnDef("file-name", "File Name", _.displayName)(),
      ColumnDef("dl-percent", "%", _.downloadedPercentOption, 45, false)(_ map (_ + "%") getOrElse ""),
      ColumnDef("dl-value", "Downloaded", _.downloadedSizeOption)(Format.fmtSizeOptionPretty),
      ColumnDef("file-size", "Size", _.sizeOption, 80)(Format.fmtSizeOptionPretty),
      ColumnDef("comment", "Comment", _.comment, 200)(),
      ColumnDef("date-created", "Added", _.dateCreated, 120)(_.toString(resources.dateTimeFmt))
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
    // TODO: Avoid excessive sorting when download progress is updated? 
    indexOfOption(de) match {
      case Some(idx) =>
        fillRow(peer.getItem(idx), de)
        sortContent()
      case None => // NOOP
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
      case (cd, i) => row.setText(i, cd.getFormattedValue(de))
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
    val colDef = peer.getColumn(colIdx).columnDef
    val items = peer.getItems
    // Selection sort
    for (i <- 0 until (items.length - 1)) {
      var minIdx = i
      val x = colDef.getValue(items(minIdx).de)
      var minValue = items(minIdx).de
      for (j <- (i + 1) until items.length) {
        val y = colDef.getValue(items(j).de)
        val value = items(j).de
        val shouldSwap: Boolean = {
          val cmp = colDef.compare(minValue, value) match {
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
    cfgMgr.setProperty(GlobalPreferences.SortColumn, column.columnDef.id)
    cfgMgr.setProperty(GlobalPreferences.SortAsc, asc)
  }

  private def loadSorting() = {
    val colId = cfgMgr.getProperty(GlobalPreferences.SortColumn)
    val asc = cfgMgr.getProperty(GlobalPreferences.SortAsc)
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
    def columnDef: ColumnDef[_] = {
      tc.getData.asInstanceOf[ColumnDef[_]]
    }
  }
}

object DownloadsTable {
  private case class ColumnDef[T: Ordering](
    id:        String, // Used to uniquely identify column
    name:      String,
    getValue:  DownloadEntryView => T,
    width:     Int                    = 0,
    resizable: Boolean                = true
  )(fmt: T => String = (x: T) => x.toString) {
    implicit val ordering = implicitly[Ordering[T]]
    def compare(de1: DownloadEntryView, de2: DownloadEntryView): Int = ordering.compare(getValue(de1), getValue(de2))
    def getFormattedValue(de: DownloadEntryView) = fmt(getValue(de))
  }

  private object Format {
    def fmtSizePretty(size: Long): String = {
      val groups = size.toString.reverse.grouped(3).map(_.reverse).toSeq.reverse
      groups.mkString("", " ", " B")
    }

    def fmtSizeOptionPretty(sizeOption: Option[Long]): String = {
      sizeOption map fmtSizePretty getOrElse ""
    }
  }
}
