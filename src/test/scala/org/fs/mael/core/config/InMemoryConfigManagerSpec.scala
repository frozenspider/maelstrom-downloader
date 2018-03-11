package org.fs.mael.core.config

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

import junit.framework.AssertionFailedError

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class InMemoryConfigManagerSpec
  extends FunSuite
  with BeforeAndAfter {

  private val setting11 = ConfigSetting("group1.1", "my-default11")
  private val setting12 = ConfigSetting("group1.2", "my-default12")
  private val setting21 = ConfigSetting("group2.1", "my-default21")
  private val setting22 = ConfigSetting("group2.2", -1)

  private var failureOption: Option[Throwable] = None

  before {
    failureOption = None
  }

  test("equality and hash code") {
    val mgr1 = new InMemoryConfigManager
    val mgr2 = new InMemoryConfigManager
    assert(mgr1 === mgr2)
    assert(mgr1.hashCode === mgr2.hashCode)

    // Setting property to its default value
    mgr2.set(setting11, setting11.default)
    assert(mgr1 === mgr2)
    assert(mgr1.hashCode === mgr2.hashCode)

    // mgr1 is modified
    mgr1.set(setting11, "my-new11")
    mgr1.set(setting12, "my-new12")
    mgr1.set(setting21, "my-new21")
    mgr1.set(setting22, 100500)
    assert(mgr1 !== mgr2)
    assert(mgr1.hashCode !== mgr2.hashCode)

    // mgr2 is modified accordingly
    mgr2.set(setting12, "my-new12")
    mgr2.set(setting22, 100500)
    mgr2.set(setting21, "my-new21")
    mgr2.set(setting11, "my-new11")
    assert(mgr1 === mgr2)
    assert(mgr1.hashCode === mgr2.hashCode)
  }

  test("reset (unconditional)") {
    val mgr1 = new InMemoryConfigManager
    val mgr2 = new InMemoryConfigManager
    mgr1.resetTo(mgr1)
    assert(mgr1 === mgr2)

    mgr2.set(setting11, "my-new11")
    mgr2.set(setting21, "my-new21")
    mgr2.set(setting22, 100500)

    mgr1.resetTo(mgr2)
    assert(mgr1 === mgr2)
    assert(mgr1(setting11) === "my-new11")
    assert(mgr1(setting12) === setting12.default)
    assert(mgr1(setting21) === "my-new21")
    assert(mgr1(setting22) === 100500)
  }

  test("reset (unconditional) - listener notification") {
    val mgr1 = new InMemoryConfigManager
    val mgr2 = new InMemoryConfigManager
    var (triggered11, triggered12, triggered21, triggered22) = (0, 0, 0, 0)
    mgr1.addSettingChangedListener(setting11)(expectPropertyChange(setting11.default, "my-new11", triggered11 != 0, triggered11 += 1))
    mgr1.addSettingChangedListener(setting12)(expectPropertyNotChanged)
    mgr1.addSettingChangedListener(setting21)(expectPropertyNotChanged)
    mgr1.addSettingChangedListener(setting22)(expectPropertyChange(setting22.default, 100500, triggered22 != 0, triggered22 += 1))

    mgr2.set(setting11, "my-new11")
    mgr2.set(setting22, 100500)

    mgr1.resetTo(mgr2)
    failureOption foreach (th => fail(th))
    assert(triggered11 === 1)
    assert(triggered12 === 0)
    assert(triggered21 === 0)
    assert(triggered22 === 1)
  }

  test("reset (conditional)") {
    val mgr1 = new InMemoryConfigManager
    val mgr2 = new InMemoryConfigManager
    mgr1.resetTo(mgr1)
    assert(mgr1 === mgr2)

    mgr2.set(setting11, "my-new11")
    mgr2.set(setting12, "my-new12")
    mgr2.set(setting21, "my-new21")

    mgr1.resetTo(mgr2, "group1")
    assert(mgr1 !== mgr2)
    assert(mgr1(setting11) === "my-new11")
    assert(mgr1(setting12) === "my-new12")
    assert(mgr1(setting21) === setting21.default)

    mgr1.resetTo(mgr2, "group2")
    assert(mgr1 !== mgr2)
    assert(mgr1(setting11) === setting11.default)
    assert(mgr1(setting12) === setting12.default)
    assert(mgr1(setting21) === "my-new21")
  }

  test("reset (conditional) - listener notification") {
    val mgr1 = new InMemoryConfigManager
    val mgr2 = new InMemoryConfigManager
    var (triggered11, triggered12, triggered21, triggered22) = (0, 0, 0, 0)
    mgr1.addSettingChangedListener(setting11)(expectPropertyNotChanged)
    mgr1.addSettingChangedListener(setting12)(expectPropertyNotChanged)
    mgr1.addSettingChangedListener(setting21)(expectPropertyChange(setting21.default, "my-new21", triggered21 != 0, triggered21 += 1))
    mgr1.addSettingChangedListener(setting22)(expectPropertyChange(setting22.default, 100500, triggered22 != 0, triggered22 += 1))

    mgr2.set(setting11, "my-new11")
    mgr2.set(setting12, "my-new12")
    mgr2.set(setting21, "my-new21")
    mgr2.set(setting22, 100500)

    mgr1.resetTo(mgr2, "group2")
    failureOption foreach (th => fail(th))
    assert(triggered11 === 0)
    assert(triggered12 === 0)
    assert(triggered21 === 1)
    assert(triggered22 === 1)
  }

  private def expectPropertyChange[T](from: T, to: T, isTriggered: => Boolean, setTriggered: => Unit) = { (e: ConfigManager.ConfigChangedEvent[T]) =>
    try {
      assert(!isTriggered)
      setTriggered
      assert(e.oldValue === from)
      assert(e.newValue === to)
    } catch {
      case th: Throwable => failureOption = Some(th)
    }
    ()
  }

  private def expectPropertyNotChanged = (e: ConfigManager.ConfigChangedEvent[_]) => {
    failureOption = Some(new AssertionFailedError("This listener should not be notified"))
    ()
  }
}
