package org.fs.mael.backend.http

import org.fs.mael.core.UserFriendlyException
import org.junit.runner.RunWith
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HttpUtilsSpec
  extends FunSuite {

  test("decode RFC 5987") {
    val d = HttpUtils.decodeRfc5987ExtValue _

    // Canonical examples from https://tools.ietf.org/html/rfc5987
    // Pound rates
    assert(d("iso-8859-1'en'%A3%20rates") === "\u00A3 rates")
    // Pound and euro rates
    assert(d("UTF-8''%c2%a3%20and%20%e2%82%ac%20rates") === "\u00A3 and \u20AC rates")

    // Custom cases
    assert(d("UTF-8''%D0%9E%D0%BD%D0%B8%20%D0%BD%D0%B5%20%D0%BF%D1%80%D0%B8%D0%BB%D0%B5%D1%82%D1%8F%D1%82%20-%20%D1%81%D0%B1%D0%BE%D1%80%D0%BD%D0%B8%D0%BA%20%D1%80%D0%B0%D1%81%D1%81%D0%BA%D0%B0%D0%B7%D0%BE%D0%B2%20%D1%87%D0%B8%D1%82%D0%B0%D0%B5%D1%82%20%D0%90.%20%D0%94%D1%83%D0%BD%D0%B8%D0%BD.zip")
      === "Они не прилетят - сборник рассказов читает А. Дунин.zip")
  }

  test("validate cookie character set") {
    val v = HttpUtils.validateCookieCharacterSet _

    // General cases
    v("qwe", "ewq")
    v("qwe", "")
    intercept[UserFriendlyException] { v("", "ewq") }
    v("a-zA-Z0-9!#$%&'*.^_`|~+-", "a-zA-Z0-9!#$%&'()*./:<=>?@[]^_`{|}~+-")

    // Illegal keys
    intercept[UserFriendlyException] { v("=", "") }
    intercept[UserFriendlyException] { v("\\", "") }
    intercept[UserFriendlyException] { v("абвгд", "") }

    // Illegal values
    intercept[UserFriendlyException] { v("qwe", "\\") }
    intercept[UserFriendlyException] { v("qwe", "абвгд") }
  }
}
