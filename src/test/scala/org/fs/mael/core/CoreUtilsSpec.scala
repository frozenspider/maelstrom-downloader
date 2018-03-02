package org.fs.mael.core

import org.fs.utility.StopWatch
import org.junit.runner.RunWith
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class CoreUtilsSpec
  extends FunSuite {

  import CoreUtils._

  test("wait until false") {
    assert(waitUntil(() => false, 100) === false)
  }

  test("wait until true") {
    assert(waitUntil(() => true, 0) === true)
  }

  test("wait until condition proc before timeout") {
    val sw = new StopWatch
    assert(waitUntil(() => {
      sw.peek >= 50
    }, 100) === true)
  }

  test("wait until condition proc after timeout") {
    val sw = new StopWatch
    assert(waitUntil(() => {
      sw.peek >= 150
    }, 100) === false)
  }

  test("with code") {
    var arr = Array.fill(1)(999).withCode { a =>
      assert(a.length === 1)
      assert(a(0) === 999)
      a(0) = -1234
      assert(a(0) === -1234)
    }
    assert(arr(0) === -1234)
  }

}
