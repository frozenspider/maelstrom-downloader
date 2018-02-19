package org.fs.mael.core.entry

import com.github.nscala_time.time.Imports._

/**
 * Download log entry.
 *
 * @author FS
 */
case class LogEntry(tpe: LogEntry.Type, date: DateTime, details: String)

object LogEntry {
  sealed trait Type
  object Info extends Type
  object Request extends Type
  object Response extends Type
  object Error extends Type

  def info(date: DateTime = DateTime.now(), details: String): LogEntry =
    LogEntry(Info, date, details)

  def request(date: DateTime = DateTime.now(), details: String): LogEntry =
    LogEntry(Request, date, details)

  def response(date: DateTime = DateTime.now(), details: String): LogEntry =
    LogEntry(Response, date, details)

  def error(date: DateTime = DateTime.now(), details: String): LogEntry =
    LogEntry(Error, date, details)
}
