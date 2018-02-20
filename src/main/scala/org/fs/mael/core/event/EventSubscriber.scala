package org.fs.mael.core.event

/**
 * Common base trait for all event subscribers.
 *
 * Subdivides onto {@code BackendSubscriber} and {@code UiSubscriber},
 * should not be used directly.
 *
 * @author FS
 */
trait EventSubscriber {
  val subscriberId: String

  override final def equals(obj: Any): Boolean = obj match {
    case es: EventSubscriber => this.subscriberId == es.subscriberId
    case _                   => false
  }

  override final lazy val hashCode: Int = subscriberId.hashCode()
}
