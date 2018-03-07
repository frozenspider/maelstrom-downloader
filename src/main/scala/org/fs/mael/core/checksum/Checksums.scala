package org.fs.mael.core.checksum

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import org.apache.commons.codec.digest.PureJavaCrc32
import org.fs.mael.core.utils.CoreUtils._

import com.google.common.primitives.Ints

object Checksums {
  val HexRegex = "[0-9a-fA-F]+"

  private val FunctionsMap: Map[ChecksumType, ChecksumImpl] = Map(
    ChecksumType.CRC32 -> CRC32,
    ChecksumType.MD5 -> MD5,
    ChecksumType.SHA1 -> SHA1,
    ChecksumType.SHA256 -> SHA256
  )

  val LengthsMap: Map[ChecksumType, Int] = FunctionsMap.mapValues(_.bytesLength)

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
    val calculated = tryWith(new FileInputStream(file)) { in =>
      FunctionsMap(checksum.tpe).calculate(in)
    }
    checksum.value == calculated
  }

  //
  // Checksum calculation classes
  //

  private sealed trait ChecksumImpl {
    def bytesLength: Int
    def calculate(in: InputStream): String
  }

  private class MessageDigestChecksum(getDigest: => MessageDigest) extends ChecksumImpl {
    override val bytesLength: Int = getDigest.getDigestLength

    override def calculate(in: InputStream): String =
      Hex.encodeHexString(DigestUtils.updateDigest(getDigest, in).digest())
  }
  private class CommonsCodecChecksum(algorithmName: String) extends MessageDigestChecksum(DigestUtils.getDigest(algorithmName))
  private object MD5 extends CommonsCodecChecksum(MessageDigestAlgorithms.MD5)
  private object SHA1 extends CommonsCodecChecksum(MessageDigestAlgorithms.SHA_1)
  private object SHA256 extends CommonsCodecChecksum(MessageDigestAlgorithms.SHA_256)
  private object CRC32 extends MessageDigestChecksum(new CRC32Digest)

  private class CRC32Digest extends MessageDigest("CRC32") {
    val inner = new PureJavaCrc32

    override val engineGetDigestLength: Int = 4

    override def engineReset(): Unit =
      inner.reset()

    override def engineUpdate(input: Byte): Unit =
      inner.update(input)

    override def engineUpdate(input: Array[Byte], offset: Int, len: Int): Unit =
      inner.update(input, offset, len)

    override def engineDigest(): Array[Byte] =
      Ints.toByteArray(inner.getValue.toInt)
  }
}
