package org.fs.mael.core.event

import org.fs.mael.core.event.Events.Progress

/**
 * Subscriber wrapper for {@code SpeedCalculator}.
 * Should be the first subscriber in the list.
 *
 * @author FS
 */
class SpeedCalcSubscriber extends UiSubscriber {
  val speedCalc = new SpeedCalculator

  override val subscriberId = "speed-calculator"

  override def fired(event: EventForUi): Unit = event match {
    case Progress(de) => speedCalc.update(de)
    case _            => // NOOP
  }
}