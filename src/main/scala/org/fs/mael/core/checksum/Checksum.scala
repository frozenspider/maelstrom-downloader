package org.fs.mael.core.checksum

case class Checksum(value: String, tpe: ChecksumType) {
  require(value == value.toLowerCase, "Checksum should be lowercase")
}
