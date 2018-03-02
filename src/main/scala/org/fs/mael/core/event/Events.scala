package org.fs.mael.core.event

sealed abstract class PriorityEvent(val priority: Int) {
  var order: Long = -1
  val msg: String
  val eventFunc: () => Unit

  override def toString(): String = {
    val fullName = this.getClass.getName
    val lastSepIdx = fullName.lastIndexWhere(c => c == '.' || c == '$')
    s"${fullName.drop(lastSepIdx + 1)}($priority, $order, $msg)"
  }
}

sealed trait UiEvent

sealed trait BackendEvent

object Events {
  case class ConfigChanged(msg: String, eventFunc: () => Unit)
    extends PriorityEvent(Int.MaxValue) with UiEvent

  case class Added(msg: String, eventFunc: () => Unit)
    extends PriorityEvent(100) with BackendEvent

  case class Removed(msg: String, eventFunc: () => Unit)
    extends PriorityEvent(100) with BackendEvent

  case class StatusChanged(msg: String, eventFunc: () => Unit)
    extends PriorityEvent(100) with BackendEvent

  case class DetailsChanged(msg: String, eventFunc: () => Unit)
    extends PriorityEvent(50) with BackendEvent

  case class Logged(msg: String, eventFunc: () => Unit)
    extends PriorityEvent(20) with BackendEvent

  case class Progress(msg: String, eventFunc: () => Unit)
    extends PriorityEvent(Int.MinValue)

}
