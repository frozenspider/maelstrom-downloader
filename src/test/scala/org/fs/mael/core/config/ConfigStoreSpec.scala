package org.fs.mael.core.config

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.prop.TableDrivenPropertyChecks

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ConfigStoreSpec
  extends FunSuite
  with BeforeAndAfter
  with TableDrivenPropertyChecks {

  private val setting1 = ConfigSetting("my.id1", "my-default1")
  private val setting2 = ConfigSetting("my.id2", -1)
  private val setting3 = ConfigSetting("my.id3", Some("my-default2"))
  private val setting4 = ConfigSetting("my.id4", Radio.r1, Radio.values)

  sealed abstract class Radio(idx: Int) extends ConfigSetting.RadioValue(idx.toString, idx + "-pretty")
  object Radio {
    object r1 extends Radio(1)
    object r2 extends Radio(2)
    object r3 extends Radio(3)
    val values = Seq(r1, r2, r3)
  }

  val settingsTable = Table(
    (("setting", "new value")),
    (setting1, "my-new"),
    (setting2, 100500),
    (setting3, Some("my-new!")),
    (setting4, Radio.r3)
  )

  test("get/set") {
    val store = new InMemoryConfigStore
    forAll(settingsTable) { (setting, newVal) =>
      assert(store(setting) === setting.default)
      assert(store.inner.isDefault(setting.id))
      set(store, setting, newVal)
      assert(store(setting) === newVal)
      assert(!store.inner.isDefault(setting.id))
    }
  }

  test("listener is notified when the value is changed") {
    val store = new InMemoryConfigStore
    forAll(settingsTable) { (setting, newVal) =>
      var triggered = false
      var failureOption: Option[Throwable] = None
      store.addSettingChangedListener(setting) { e =>
        try {
          assert(!triggered)
          triggered = true
          assert(e.oldValue === setting.default)
          assert(e.newValue === newVal)
        } catch {
          case th: Throwable => failureOption = Some(th)
        }
      }
      set(store, setting, newVal)
      assert(triggered)
      assert(setting.get(store.inner) == newVal)
      failureOption foreach (th => fail(th))
    }
  }

  test("listener is NOT notified when default value is set explicitly") {
    val store = new InMemoryConfigStore
    forAll(settingsTable) { (setting, _) =>
      var triggered = false
      store.addSettingChangedListener(setting) { v =>
        triggered = true
      }
      assert(store.inner.getString(setting.id) === "")
      set(store, setting, setting.default)
      assert(!triggered)
      // However, value is still updated
      assert(setting.get(store.inner) == setting.default)
    }
  }

  private def set[T](store: IConfigStoreImpl, setting: ConfigSetting[T], newVal: Any): Unit =
    store.set(setting, newVal.asInstanceOf[T])
}
