package org.fs.mael.core.checksum

import java.io.File
import java.io.FileInputStream

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import org.fs.mael.core.utils.CoreUtils._

object Checksums {
  val HexRegex = "[0-9a-fA-F]+"

  def isProper(tpe: ChecksumType, str: String): Boolean = {
    if (!(str matches HexRegex)) {
      false
    } else {
      val digest = DigestUtils.getDigest(getAlgorithmName(tpe))
      str.length == (digest.getDigestLength * 2)
    }
  }

  def check(checksum: Checksum, file: File): Boolean = {
    val digest = DigestUtils.getDigest(getAlgorithmName(checksum.tpe))
    val calculated = tryWith(new FileInputStream(file)) { in =>
      Hex.encodeHexString(DigestUtils.updateDigest(digest, in).digest())
    }
    checksum.value == calculated
  }

  private def getAlgorithmName(tpe: ChecksumType): String = tpe match {
    case ChecksumType.MD5    => MessageDigestAlgorithms.MD5
    case ChecksumType.SHA1   => MessageDigestAlgorithms.SHA_1
    case ChecksumType.SHA256 => MessageDigestAlgorithms.SHA_256
  }
}
