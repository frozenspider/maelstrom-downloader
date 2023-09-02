package org.fs.mael.core.utils

import java.io.Closeable

import org.fs.mael.core.UserFriendlyException
import org.fs.utility.StopWatch
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[org.scalatestplus.junit.JUnitRunner])
class CoreUtilsSpec
  extends AnyFunSuite {

  import CoreUtils._

  test("requireFriendly") {
    requireFriendly(true, "msg")
    requireFriendly(true, ???)
    val ex = intercept[UserFriendlyException] {
      requireFriendly(false, "msg")
    }
    assert(ex.getMessage === "msg")
  }

  test("failFriendly") {
    val ex = intercept[UserFriendlyException] {
      failFriendly("msg")
    }
    assert(ex.getMessage === "msg")
  }

  test("waitUntil false") {
    assert(waitUntil(100)(false) === false)
  }

  test("waitUntil true") {
    assert(waitUntil(0)(true) === true)
  }

  test("waitUntil condition proc before timeout") {
    val sw = new StopWatch
    assert(waitUntil(100) {
      sw.peek >= 50
    } === true)
  }

  test("waitUntil condition proc after timeout") {
    val sw = new StopWatch
    assert(waitUntil(100) {
      sw.peek >= 200
    } === false)
  }

  test("withCode") {
    var arr = Array.fill(1)(999).withCode { a =>
      assert(a.length === 1)
      assert(a(0) === 999)
      a(0) = -1234
      assert(a(0) === -1234)
    }
    assert(arr(0) === -1234)
  }

  test("tryWith") {
    var isClosed = false
    var codeProcessed = false
    val cl = new Closeable {
      override def close(): Unit = isClosed = true
    }
    assert(!isClosed)
    tryWith(cl)(cl2 => {
      assert(cl2 === cl)
      codeProcessed = true
    })
    assert(codeProcessed)
    assert(isClosed)

    isClosed = false
    codeProcessed = false
    intercept[IllegalArgumentException] {
      tryWith(cl)(cl2 => {
        throw new IllegalArgumentException
        codeProcessed = true
      })
    }
    assert(!codeProcessed)
    assert(isClosed)
  }
}
