package org.fs.mael.core.config

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.prop.TableDrivenPropertyChecks

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ConfigManagerSpec
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
    val mgr = new InMemoryConfigManager
    forAll(settingsTable) { (setting, newVal) =>
      assert(mgr(setting) === setting.default)
      assert(mgr.store.isDefault(setting.id))
      set(mgr, setting, newVal)
      assert(mgr(setting) === newVal)
      assert(!mgr.store.isDefault(setting.id))
    }
  }

  test("listener is notified when the value is changed") {
    val mgr = new InMemoryConfigManager
    forAll(settingsTable) { (setting, newVal) =>
      var triggered = false
      var failureOption: Option[Throwable] = None
      mgr.addSettingChangedListener(setting) { e =>
        try {
          assert(!triggered)
          triggered = true
          assert(e.oldValue === setting.default)
          assert(e.newValue === newVal)
        } catch {
          case th: Throwable => failureOption = Some(th)
        }
      }
      set(mgr, setting, newVal)
      assert(triggered)
      assert(setting.get(mgr.store) == newVal)
      failureOption foreach (th => fail(th))
    }
  }

  test("listener is NOT notified when default value is set explicitly") {
    val mgr = new InMemoryConfigManager
    forAll(settingsTable) { (setting, _) =>
      var triggered = false
      mgr.addSettingChangedListener(setting) { v =>
        triggered = true
      }
      assert(mgr.store.getString(setting.id) === "")
      set(mgr, setting, setting.default)
      assert(!triggered)
      // However, value is still updated
      assert(setting.get(mgr.store) == setting.default)
    }
  }

  private def set[T](mgr: ConfigManager, setting: ConfigSetting[T], newVal: Any): Unit =
    mgr.set(setting, newVal.asInstanceOf[T])
}
