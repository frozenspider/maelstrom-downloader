package org.fs.mael.ui.components

import org.eclipse.swt.SWT
import org.eclipse.swt.widgets._
import org.fs.mael.core.entry.DownloadEntryView
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.resources.Resources
import org.fs.mael.ui.utils.SwtUtils._

class LogTable(
  parent:    Composite,
  resources: Resources
) extends MUiComponent[Table](parent) {

  private var _currentOption: Option[DownloadEntryView] = None

  private val columnDefs = Seq(
    ColumnDef("", 24),
    ColumnDef("Date", 80),
    ColumnDef("Time", 80),
    ColumnDef("Information", 500)
  )

  override val peer: Table = {
    val table = new Table(parent, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION)
    table.setLinesVisible(true)
    table.setHeaderVisible(true)

    columnDefs.foreach { h =>
      val c = new TableColumn(table, SWT.NONE)
      c.setText(h.name)
      c.setWidth(h.width)
    }

    // Since standard images in table remove background, we have to draw them manually instead
    table.addListener(SWT.PaintItem, e => {
      val row = e.item.asInstanceOf[TableItem]
      row.getData match {
        case entry: LogEntry =>
          val icon = resources.icon(entry.tpe)
          val rowBounds = row.getBounds
          val iconBounds = icon.getBounds
          val offset = (rowBounds.height - iconBounds.height) / 2
          e.gc.drawImage(icon, rowBounds.x + offset, rowBounds.y + offset)
        case _ => // NOOP
      }
    })

    table.getColumns.filter(_.getWidth == 0).foreach(_.pack())
    table
  }

  def render(de: DownloadEntryView): Unit = {
    peer.removeAll()
    de.downloadLog.foreach(append(_, true))
    scrollTableToBottom(peer)
  }

  def render(deOption: Option[DownloadEntryView]): Unit = {
    _currentOption = deOption
    deOption map render getOrElse { peer.removeAll() }
  }

  def currentOption: Option[DownloadEntryView] = _currentOption

  def append(entry: LogEntry, dontScroll: Boolean): Unit = {
    val lines = entry.details.trim.split("\n")
    val wasShowingLastRow =
      if (peer.getItemCount > 0) {
        val prevLastRow = peer.getItem(peer.getItemCount - 1)
        isRowVisible(prevLastRow)
      } else true
    new TableItem(peer, SWT.NONE).withCode { row =>
      row.setData(entry)
      row.setText(1, entry.date.toString(resources.dateFmt))
      row.setText(2, entry.date.toString(resources.timeFmt))
      row.setText(3, lines.head.trim)
      row.setBackground(resources.logColor(entry.tpe, display))
    }
    lines.tail.foreach { line =>
      new TableItem(peer, SWT.NONE).withCode { row =>
        row.setText(3, line.trim)
        row.setBackground(resources.logColor(entry.tpe, display))
      }
    }
    if (!dontScroll && wasShowingLastRow) scrollTableToBottom(peer)
  }

  case class ColumnDef(name: String, width: Int = 0)
}
