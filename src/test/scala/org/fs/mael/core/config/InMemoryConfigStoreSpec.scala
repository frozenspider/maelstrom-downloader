package org.fs.mael.core.config

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[org.scalatestplus.junit.JUnitRunner])
class InMemoryConfigStoreSpec
  extends AnyFunSuite
  with BeforeAndAfter
  with BeforeAndAfterAll {

  private lazy val (setting11, setting12, setting21, setting22) = (
    ConfigSetting("group1.1", "my-default11"),
    ConfigSetting("group1.2", -1),
    ConfigSetting("group2.1", Radio.r1, Radio.values),
    ConfigSetting("group2.2", Some("my-default22"))
  )

  sealed abstract class Radio(idx: Int) extends ConfigSetting.RadioValue(idx.toString, idx + "-pretty")
  object Radio {
    object r1 extends Radio(1)
    object r2 extends Radio(2)
    object r3 extends Radio(3)
    val values = Seq(r1, r2, r3)
  }

  private var failureOption: Option[Throwable] = None

  override def afterAll() {
    ConfigSetting.test_clearRegistry()
  }

  before {
    failureOption = None
  }

  test("access, modification, equality and hash code") {
    val store1 = new InMemoryConfigStore
    val store2 = new InMemoryConfigStore
    assert(store1 === store2)
    assert(store1.hashCode === store2.hashCode)

    // Setting property to its default value
    store2.set(setting11, setting11.default)
    assert(store1 === store2)
    assert(store1.hashCode === store2.hashCode)

    // store1 is modified
    store1.set(setting11, "my-new11")
    store1.set(setting12, 100500)
    store1.set(setting21, Radio.r2)
    store1.set(setting22, Some("my-new22"))
    assert(store1 !== store2)
    assert(store1.hashCode !== store2.hashCode)

    // store2 is modified accordingly
    store2.set(setting12, 100500)
    store2.set(setting22, Some("my-new22"))
    store2.set(setting21, Radio.r2)
    store2.set(setting11, "my-new11")
    assert(store1 === store2)
    assert(store1.hashCode === store2.hashCode)
  }
}
