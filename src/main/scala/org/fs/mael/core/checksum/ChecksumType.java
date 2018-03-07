package org.fs.mael.core.checksum;

public enum ChecksumType {
  MD5(16), SHA1(20), SHA256(32);

  /** Bytes per hash */
  final int length;

  ChecksumType(int length) {
    this.length = length;
  }
}
