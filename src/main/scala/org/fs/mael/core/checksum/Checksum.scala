package org.fs.mael.core.checksum

case class Checksum(tpe: ChecksumType, value: String) {
  require(value == value.toLowerCase, "Checksum should be lowercase")
}
