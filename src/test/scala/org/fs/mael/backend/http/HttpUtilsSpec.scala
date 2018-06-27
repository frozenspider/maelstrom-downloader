package org.fs.mael.backend.http

import scala.collection.immutable.ListMap

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

  test("parse client cookies") {
    val p = HttpUtils.parseClientCookies _

    // Google search
    assert(p("Cookie: "
      + "1P_JAR=2018-06-27-15; "
      + "NID=133=hpjOnZWhyu_sOZoGR9McAvXcruhNHeAi212P2Mz5VzhVTqmTG4qe4cd2guiq344hgObQY5QzG8O9TX8o9-FzMJvC4SmTeUo2S4a1pIEinC08ZRN_BRNspq6f68hN_B8F; "
      + "APISID=1EwD5_BAx04fHU22/A_1BhR2dudWqncydX; "
      + "OGP=-5061451:")
      === ListMap(
        "1P_JAR" -> "2018-06-27-15",
        "NID" -> "133=hpjOnZWhyu_sOZoGR9McAvXcruhNHeAi212P2Mz5VzhVTqmTG4qe4cd2guiq344hgObQY5QzG8O9TX8o9-FzMJvC4SmTeUo2S4a1pIEinC08ZRN_BRNspq6f68hN_B8F",
        "APISID" -> "1EwD5_BAx04fHU22/A_1BhR2dudWqncydX",
        "OGP" -> "-5061451:"
      ))
    // StackOverflow
    assert(p("Cookie: "
      + "_ga=GA1.2.463278568.1382713854; "
      + "__hstc=104375039.7bf30a8f3502ef7923f2bb559bf9a992.1429404550127.1483013765730.1486018725052.4; "
      + "hubspotutk=7af30a8f3502ef7923f2bb559bf9a992; "
      + "prov=137476a9-015e-43e1-aa87-1c6a968e2438; "
      + "cc=ad289322b1064442a225af18887e1ebd; "
      + "_ym_uid=1476221257350236490; "
      + "acct=t=SapsnqQflCuLG%2fWZahMZdE%2fNDzLLeQN2&s=WQeiW8%2fTzAu%2bSuhISWRsakSu3tz0N9Wg; "
      + "mfnes=6d1fCCIQAxoLCLzV7MqJlK42EAUgCigBMghjMDc3NjNkYg==; "
      + "__qca=P0-1652149249-1528687764649; "
      + "interest-tour-dismissed=1; "
      + "_gid=GA1.2.1130758561.1529901128")
      === ListMap(
        "_ga" -> "GA1.2.463278568.1382713854",
        "__hstc" -> "104375039.7bf30a8f3502ef7923f2bb559bf9a992.1429404550127.1483013765730.1486018725052.4",
        "hubspotutk" -> "7af30a8f3502ef7923f2bb559bf9a992",
        "prov" -> "137476a9-015e-43e1-aa87-1c6a968e2438",
        "cc" -> "ad289322b1064442a225af18887e1ebd",
        "_ym_uid" -> "1476221257350236490",
        "acct" -> "t=SapsnqQflCuLG%2fWZahMZdE%2fNDzLLeQN2&s=WQeiW8%2fTzAu%2bSuhISWRsakSu3tz0N9Wg",
        "mfnes" -> "6d1fCCIQAxoLCLzV7MqJlK42EAUgCigBMghjMDc3NjNkYg==",
        "__qca" -> "P0-1652149249-1528687764649",
        "interest-tour-dismissed" -> "1",
        "_gid" -> "GA1.2.1130758561.1529901128"
      ))
  }
}
