package org.fs.mael.core.entry

import com.github.nscala_time.time.Imports._

/**
 * Download log entry.
 *
 * @author FS
 */
case class LogEntry(tpe: LogEntry.Type, date: DateTime, details: String)

object LogEntry {
  sealed trait Type {
    override val toString = this.getClass.getSimpleName.replaceAllLiterally("$", "")
  }
  object Info extends Type
  object Request extends Type
  object Response extends Type
  object Error extends Type

  def info(details: String): LogEntry =
    info(DateTime.now(), details)

  def info(date: DateTime, details: String): LogEntry =
    LogEntry(Info, date, details)

  def request(details: String): LogEntry =
    request(DateTime.now(), details)

  def request(date: DateTime, details: String): LogEntry =
    LogEntry(Request, date, details)

  def response(details: String): LogEntry =
    response(DateTime.now(), details)

  def response(date: DateTime, details: String): LogEntry =
    LogEntry(Response, date, details)

  def error(details: String): LogEntry =
    error(DateTime.now(), details)

  def error(date: DateTime, details: String): LogEntry =
    LogEntry(Error, date, details)
}
