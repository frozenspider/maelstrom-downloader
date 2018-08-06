package org.fs.mael.core.config

import java.util.UUID

import org.fs.mael.core.config.ConfigSetting._
import org.fs.mael.test.TestUtils
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

import junit.framework.AssertionFailedError

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class BackendConfigStoreSpec
  extends FunSuite
  with BeforeAndAfter {
  import org.fs.mael.test.TestUtils.ConfigValueClasses._

  private val (setting11, setting12, setting21, setting22) = {
    ConfigSetting.test_clearRegistry()
    (
      ConfigSetting("group1.1", "my-default11"),
      ConfigSetting("group1.2", -1),
      ConfigSetting("group2.1", Radio.r1, Radio.values),
      ConfigSetting("group2.2", Some("my-default22"))
    )
  }

  private val settingAbcs = new SeqConfigSetting[ABC]("group1.abcs", AbcClassses)
  private val settingAbc = new RefConfigSetting("group1.abc", A, settingAbcs)
  private val settingAbcLocal = new LocalEntityConfigSetting[ABC]("group1.local.abc", settingAbc, settingAbcs, AbcClassses)

  sealed abstract class Radio(idx: Int) extends ConfigSetting.RadioValue(idx.toString, idx + "-pretty")
  object Radio {
    object r1 extends Radio(1)
    object r2 extends Radio(2)
    object r3 extends Radio(3)
    val values = Seq(r1, r2, r3)
  }

  private var failureOption: Option[Throwable] = None

  before {
    failureOption = None
  }

  test("access, modification, equality and hash code") {
    val store1 = BackendConfigStore(TestUtils.emptyGlobalCfg(), TestUtils.DummySettingsAccessChecker)
    val store2 = BackendConfigStore(TestUtils.emptyGlobalCfg(), TestUtils.DummySettingsAccessChecker)
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

  test("access restrictions") {
    val store1 = BackendConfigStore(TestUtils.emptyGlobalCfg(), new DefaultSettingsAccessChecker("group1"))
    val store2 = BackendConfigStore(TestUtils.emptyGlobalCfg(), new DefaultSettingsAccessChecker("group1"))
    assert(store1 === store2)

    // Modifying allowed settings
    assert(store1(setting11) === "my-default11")
    assert(store1(setting12) === -1)
    store1.set(setting11, "my-new11")
    store1.set(setting12, 100500)
    assert(store1(setting11) === "my-new11")
    assert(store1(setting12) === 100500)
    assert(store1 !== store2)
    assert(store1.hashCode !== store2.hashCode)

    // Trying to access disallowed settings
    intercept[IllegalArgumentException] {
      store1.set(setting21, Radio.r2)
    }
    intercept[IllegalArgumentException] {
      store1(setting22)
    }
    intercept[IllegalArgumentException] {
      store1.addSettingChangedListener(setting21)(expectPropertyNotChanged)
    }
  }

  test("reset (unconditional)") {
    val store1 = BackendConfigStore(TestUtils.emptyGlobalCfg(), TestUtils.DummySettingsAccessChecker)
    val store2 = BackendConfigStore(TestUtils.emptyGlobalCfg(), TestUtils.DummySettingsAccessChecker)
    store1.resetTo(store1)
    assert(store1 === store2)

    store2.set(setting11, "my-new11")
    store2.set(setting21, Radio.r2)
    store2.set(setting22, None)

    store1.resetTo(store2)
    assert(store1 === store2)
    assert(store1(setting11) === "my-new11")
    assert(store1(setting12) === setting12.default)
    assert(store1(setting21) === Radio.r2)
    assert(store1(setting22) === None)
  }

  test("reset (unconditional) - listener notification") {
    val store1 = BackendConfigStore(TestUtils.emptyGlobalCfg(), TestUtils.DummySettingsAccessChecker)
    val store2 = BackendConfigStore(TestUtils.emptyGlobalCfg(), TestUtils.DummySettingsAccessChecker)
    var (triggered11, triggered12, triggered21, triggered22) = (0, 0, 0, 0)
    store1.addSettingChangedListener(setting11)(expectPropertyChange(setting11.default, "my-new11", triggered11 != 0, triggered11 += 1))
    store1.addSettingChangedListener(setting12)(expectPropertyNotChanged)
    store1.addSettingChangedListener(setting21)(expectPropertyNotChanged)
    store1.addSettingChangedListener(setting22)(expectPropertyChange(setting22.default, None, triggered22 != 0, triggered22 += 1))

    store2.set(setting11, "my-new11")
    store2.set(setting22, None)

    store1.resetTo(store2)
    failureOption foreach (th => fail(th))
    assert(triggered11 === 1)
    assert(triggered12 === 0)
    assert(triggered21 === 0)
    assert(triggered22 === 1)
  }

  test("reset (conditional)") {
    val store11 = BackendConfigStore(TestUtils.emptyGlobalCfg(), new DefaultSettingsAccessChecker("group1"))
    val store2 = BackendConfigStore(TestUtils.emptyGlobalCfg(), TestUtils.DummySettingsAccessChecker)
    assert(store11 !== store2)

    store2.set(setting11, "my-new11")
    store2.set(setting12, 100500)
    store2.set(setting21, Radio.r2)

    store11.resetTo(store2)
    assert(store11(setting11) === "my-new11")
    assert(store11(setting12) === 100500)

    val store12 = BackendConfigStore(TestUtils.emptyGlobalCfg(), new DefaultSettingsAccessChecker("group2"))
    store12.resetTo(store2)
    assert(store12(setting21) === Radio.r2)
  }

  test("reset (conditional) - listener notification") {
    val store1 = BackendConfigStore(TestUtils.emptyGlobalCfg(), new DefaultSettingsAccessChecker("group2"))
    val store2 = BackendConfigStore(TestUtils.emptyGlobalCfg(), TestUtils.DummySettingsAccessChecker)
    var (triggered21, triggered22) = (0, 0)
    store1.addSettingChangedListener(setting21)(expectPropertyChange(setting21.default, Radio.r2, triggered21 != 0, triggered21 += 1))
    store1.addSettingChangedListener(setting22)(expectPropertyChange(setting22.default, Some("my-new22"), triggered22 != 0, triggered22 += 1))

    store2.set(setting11, "my-new11")
    store2.set(setting12, 100500)
    store2.set(setting21, Radio.r2)
    store2.set(setting22, Some("my-new22"))

    store1.resetTo(store2)
    failureOption foreach (th => fail(th))
    assert(triggered21 === 1)
    assert(triggered22 === 1)
  }

  test("local settings") {
    import LocalConfigSettingValue._
    val gStore = TestUtils.emptyGlobalCfg()
    val lStore = BackendConfigStore(gStore, new DefaultSettingsAccessChecker("group1"))
    assert(lStore(settingAbcLocal) === Default)
    assert(lStore.resolve(settingAbcLocal) === A)
    intercept[IllegalArgumentException] {
      gStore(settingAbcLocal)
    }

    val (b, c1, c2) = (B(UUID.randomUUID()), C(UUID.randomUUID(), "v1", 1), C(UUID.randomUUID(), "v2", 2))

    // Updating seq
    gStore.set(settingAbcs, Seq(b, c1))
    assert(lStore(settingAbcLocal) === Default)
    assert(lStore.resolve(settingAbcLocal) === A)

    // Updating ref to invalid
    gStore.set(settingAbc, c2.uuid)
    assert(lStore(settingAbcLocal) === Default)
    assert(lStore.resolve(settingAbcLocal) === A)

    // Updating ref to valid
    gStore.set(settingAbc, c1.uuid)
    assert(lStore(settingAbcLocal) === Default)
    assert(lStore.resolve(settingAbcLocal) === c1)
    assert(lStore.resolve(settingAbcLocal) !== A)

    // Updating seq making ref invalid
    gStore.set(settingAbcs, Seq(b, c2))
    assert(lStore(settingAbcLocal) === Default)
    assert(lStore.resolve(settingAbcLocal) === A)

    // Updating local ref to invalid
    lStore.set(settingAbcLocal, Ref(c1.uuid))
    assert(lStore(settingAbcLocal) === Ref(c1.uuid))
    assert(lStore.resolve(settingAbcLocal) === A)

    // Updating local ref to valid
    lStore.set(settingAbcLocal, Ref(c2.uuid))
    assert(lStore(settingAbcLocal) === Ref(c2.uuid))
    assert(lStore.resolve(settingAbcLocal) === c2)

    // Updating local ref to embedded
    lStore.set(settingAbcLocal, Embedded(c1))
    assert(lStore(settingAbcLocal) === Embedded(c1))
    assert(lStore.resolve(settingAbcLocal) === c1)
  }

  private def expectPropertyChange[T](from: T, to: T, isTriggered: => Boolean, setTriggered: => Unit) = { (e: ConfigChangedEvent[T]) =>
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

  private def expectPropertyNotChanged = (e: ConfigChangedEvent[_]) => {
    failureOption = Some(new AssertionFailedError("This listener should not be notified"))
    ()
  }
}
