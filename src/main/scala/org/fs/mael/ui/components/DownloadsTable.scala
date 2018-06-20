package org.fs.mael.ui.components

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.TableColumn
import org.eclipse.swt.widgets.TableItem
import org.fs.mael.core.config.ConfigStore
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.speed.SpeedTracker
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.components.DownloadsTable._
import org.fs.mael.ui.config.GlobalSettings
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._

import com.github.nscala_time.time.Imports._

class DownloadsTable(
  parent:    Composite,
  resources: Resources,
  globalCfg: ConfigStore
) extends MUiComponent[Table](parent) {

  // TODO: Save/restore column widths
  // TODO: Reorder columns
  private val columnDefs: IndexedSeq[ColumnDef] = {
    IndexedSeq(
      ColumnFormattedDef("file-name", "File Name", _.displayName)(),
      ColumnFormattedDef("dl-percent", "%", _.downloadedPercentOption, 45, false)(_ map (_ + "%") getOrElse ""),
      ColumnFormattedDef("dl-value", "Downloaded", _.downloadedSizeOption)(Format.fmtSizeOptionPretty),
      ColumnFormattedDef("file-size", "Size", _.sizeOption, 80)(Format.fmtSizeOptionPretty),
      ColumnValuelessDef("speed", "Speed", 90),
      ColumnValuelessDef("eta", "ETA", 60),
      ColumnFormattedDef("comment", "Comment", _.comment, 200)(),
      ColumnFormattedDef("date-created", "Added", _.dateCreated, 120)(_.toString(resources.dateTimeFmt))
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
      if (cd.ordered) {
        c.addListener(SWT.Selection, sortListener)
      }
    }

    installDefaultHotkeys(table)
    table
  }

  loadSorting()

  /** Return all selected entries */
  def selectedEntries: Seq[DownloadEntry] = {
    peer.getSelection map (_.de)
  }

  /** Return a singular selected entry. If multiple entries are selected, returns {@code None}. */
  def selectedEntryOption: Option[DownloadEntry] = {
    if (peer.getSelectionCount == 1) {
      selectedEntries.headOption
    } else {
      None
    }
  }

  def indexOfOption(de: DownloadEntry): Option[Int] = {
    peer.getItems.indexWhere(_.de match {
      case de2 if de2.id == de.id => true
      case _                      => false
    }) match {
      case -1 => None
      case x  => Some(x)
    }
  }

  def init(entries: Iterable[DownloadEntry]): Unit = {
    require(peer.getItemCount == 0, "Table isn't empty")
    val sorted = entries.toSeq.sortBy(_.dateCreated)
    require(sorted.map(_.dateCreated).distinct == sorted.map(_.dateCreated), "Download list contains entries with the same created date")
    sorted.foreach { de =>
      val newRow = new TableItem(peer, SWT.NONE)
      fillRow(newRow, de)
    }
    adjustColumnWidths()
    sortContentCurr()
    fireSelectionUpdated()
  }

  def add(de: DownloadEntry): Unit = {
    val newRow = new TableItem(peer, SWT.NONE)
    fillRow(newRow, de)
    peer.deselectAll()
    peer.showItem(newRow)
    peer.select(peer.getItems.indexOf(newRow))
    sortContentCurr()
    fireSelectionUpdated()
  }

  def remove(de: DownloadEntry): Unit = {
    indexOfOption(de) foreach { idx =>
      peer.remove(idx)
      fireSelectionUpdated()
    }
  }

  def update(de: DownloadEntry): Unit = {
    // TODO: Do not table scroll if entity is selected
    // TODO: Avoid excessive sorting when download progress is updated?
    indexOfOption(de) match {
      case Some(idx) =>
        fillRow(peer.getItem(idx), de)
        sortContentCurr()
      case None => // NOOP
    }
  }

  def updateSpeedEta(de: DownloadEntry, speedOption: Option[Long], etaSecondsOption: Option[Long]): Unit = {
    indexOfOption(de) match {
      case Some(idx) =>
        val row = peer.getItem(idx)
        row.setText(columnDefs.indexWhere(_.id == "speed"), Format.fmtSpeedOptionPretty(speedOption))
        row.setText(columnDefs.indexWhere(_.id == "eta"), Format.fmtTimeOptionPretty(etaSecondsOption))
      case None => // NOOP
    }
  }

  private def fireSelectionUpdated(): Unit = {
    // We could fill the event properly, but our code doesn't care about event details
    peer.notifyListeners(SWT.Selection, new Event())
  }

  private def fillRow(row: TableItem, de: DownloadEntry): Unit = {
    row.setData(de)
    row.setImage(0, resources.icon(de.status))
    columnDefs.zipWithIndex.foreach {
      case (cd, i) => cd.getFormattedValueOption(de) map (fv => row.setText(i, fv))
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
    sortContentCurr()
  }

  /** Sort table content according to currently set sort column and direction */
  private def sortContentCurr(): Unit = {
    val colIdx = peer.getColumns.indexOf(peer.getSortColumn)
    if (colIdx != -1) {
      peer.getColumn(colIdx).columnDef match {
        case colDef: ColumnFormattedDef[_] => sortContentCurrInner(colIdx, colDef)
        case _                             => // Not sortable, NOOP
      }
    }
  }

  private def sortContentCurrInner(colIdx: Int, colDef: ColumnFormattedDef[_]): Unit = {
    val oldSelectedData = peer.getSelection.map(_.de).toSet
    val asc = peer.getSortDirection == SWT.UP
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
    globalCfg.set(GlobalSettings.SortColumn, column.columnDef.id)
    globalCfg.set(GlobalSettings.SortAsc, asc)
  }

  private def loadSorting() = {
    val colId = globalCfg(GlobalSettings.SortColumn)
    val asc = globalCfg(GlobalSettings.SortAsc)
    val colOption = peer.getColumns.find(_.columnDef.id == colId)
    colOption foreach { col =>
      sortContent(col, asc)
    }
  }

  private implicit class RichTableItem(ti: TableItem) {
    def de: DownloadEntry = {
      ti.getData.asInstanceOf[DownloadEntry]
    }
  }

  private implicit class RichTableColumn(tc: TableColumn) {
    def columnDef: ColumnDef = {
      tc.getData.asInstanceOf[ColumnDef]
    }
  }
}

object DownloadsTable {
  private sealed trait ColumnDef {
    /** Used to uniquely identify column */
    def id: String
    def name: String
    def width: Int
    def resizable: Boolean
    def ordered = false
    /** Returns this column's formatted value for the given entity, if applicable */
    def getFormattedValueOption(de: DownloadEntry): Option[String] = None
  }

  private case class ColumnFormattedDef[T: Ordering](
    id:        String,
    name:      String,
    getValue:  DownloadEntry => T,
    width:     Int                = 0,
    resizable: Boolean            = true
  )(fmt: T => String = (x: T) => x.toString) extends ColumnDef {
    override val ordered = true
    override def getFormattedValueOption(de: DownloadEntry) = Some(getFormattedValue(de))

    implicit val ordering = implicitly[Ordering[T]]
    def compare(de1: DownloadEntry, de2: DownloadEntry): Int = ordering.compare(getValue(de1), getValue(de2))
    def getFormattedValue(de: DownloadEntry) = fmt(getValue(de))
  }

  private case class ColumnValuelessDef(
    id:        String,
    name:      String,
    width:     Int     = 0,
    resizable: Boolean = true
  ) extends ColumnDef

  private object Format {
    def fmtSizePretty(size: Long, suffix: String): String = {
      val groups = size.toString.reverse.grouped(3).map(_.reverse).toSeq.reverse
      groups.mkString("", " ", suffix)
    }

    def fmtSizePretty(size: Long): String = {
      fmtSizePretty(size, " B")
    }

    def fmtSizeOptionPretty(sizeOption: Option[Long]): String = {
      sizeOption map fmtSizePretty getOrElse ""
    }

    def fmtSpeedOptionPretty(speedOption: Option[Long]): String = {
      speedOption map (speed => fmtSizePretty(speed, " B/s")) getOrElse ""
    }

    def fmtTimePretty(seconds: Long): String = {
      seconds match {
        case s if s >= 86400 => (s / 86400) + "d"
        case s if s >= 36000 => (s / 3600) + "h" // >10h
        case s if s >= 3600  => (s / 3600) + "h " + fmtTimePretty(s % 3600)
        case s if s >= 600   => (s / 60) + "m" // >10m
        case s if s >= 60    => (s / 60) + "m " + fmtTimePretty(s % 60)
        case s               => s + "s"
      }
    }

    def fmtTimeOptionPretty(secondsOption: Option[Long]): String = {
      secondsOption map (fmtTimePretty) getOrElse ""
    }
  }
}
