package org.fs.mael.backend.http.ui

import scala.collection.immutable.ListMap

import org.eclipse.swt.widgets._
import org.fs.mael.backend.http.utils.HttpUtils
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.components.StringTablePopupEditorDialog
import org.slf4s.Logging

class HeadersFieldEditorDialog(parent: Shell, initialCookiesMap: ListMap[String, String])
  extends StringTablePopupEditorDialog("Headers Editor", parent, initialCookiesMap)
  with Logging {

  override protected lazy val nameColumnHeader: String = "Header name"
  override protected lazy val valueColumnHeader: String = "Header value"
  override protected lazy val removeEntryTooltipText: String = "Remove header entry"

  override protected def validateAndGet(nameValPairs: IndexedSeq[(String, String)]): ListMap[String, String] = {
    val nameValPairs2 = nameValPairs filter {
      case (k, v) => !k.isEmpty && !v.isEmpty
    }

    val duplicates = nameValPairs2.groupBy(_._1).collect { case (n, vs) if vs.size > 1 => (n, vs.size) }
    requireFriendly(duplicates.size == 0, "Duplicate headers: " + duplicates.keys.mkString(", "))
    nameValPairs2.foreach {
      case (k, v) => HttpUtils.validateHeaderCharacterSet(k, v)
    }
    ListMap(nameValPairs2: _*)
  }
}
