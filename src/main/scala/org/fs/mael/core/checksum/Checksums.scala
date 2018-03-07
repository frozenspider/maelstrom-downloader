package org.fs.mael.core.checksum

import java.io.File
import java.io.FileInputStream

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import org.fs.mael.core.utils.CoreUtils._

object Checksums {
  val HexRegex = "[0-9a-fA-F]+"

  val LengthsMap: Map[ChecksumType, Int] = ChecksumType.values().map(ct => (ct, ct.length)).toMap

  def isProper(tpe: ChecksumType, str: String): Boolean = {
    if (!(str matches HexRegex)) {
      false
    } else {
      str.length == (LengthsMap(tpe) * 2)
    }
  }

  def guessType(str: String): Option[ChecksumType] = {
    val confirming = Checksums.LengthsMap filter (str.length == _._2 * 2)
    if (confirming.size == 1) {
      val guess = confirming.head._1
      if (isProper(guess, str))
        Some(confirming.head._1)
      else
        None
    } else {
      None
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
