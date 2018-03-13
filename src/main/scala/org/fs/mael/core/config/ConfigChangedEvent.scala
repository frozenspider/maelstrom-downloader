package org.fs.mael.core.config

case class ConfigChangedEvent[T](oldValue: T, newValue: T)
