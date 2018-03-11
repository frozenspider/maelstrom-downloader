package org.fs.mael.core.config

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

@RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ConfigManagerSpec
  extends FunSuite
  with BeforeAndAfter {

  private val setting1 = ConfigSetting("my.id1", "my-default1")

  test("get/set") {
    val mgr = new InMemoryConfigManager
    assert(mgr(setting1) === setting1.default)
    assert(mgr.store.isDefault(setting1.id))
    mgr.set(setting1, "my-new")
    assert(mgr(setting1) === "my-new")
    assert(!mgr.store.isDefault(setting1.id))
  }

  test("listener is notified when the value is changed") {
    val mgr = new InMemoryConfigManager
    var triggered = false
    var failureOption: Option[Throwable] = None
    mgr.addSettingChangedListener(setting1) { e =>
      try {
        assert(!triggered)
        triggered = true
        assert(e.oldValue === setting1.default)
        assert(e.newValue === "my-new")
      } catch {
        case th: Throwable => failureOption = Some(th)
      }
    }
    mgr.set(setting1, "my-new")
    assert(triggered)
    assert(mgr.store.getString(setting1.id) === "my-new")
    failureOption foreach (th => fail(th))
  }

  test("listener is NOT notified when default value is set explicitly") {
    val mgr = new InMemoryConfigManager
    var triggered = false
    mgr.addSettingChangedListener(setting1) { v =>
      triggered = true
    }
    assert(mgr.store.getString(setting1.id) === "")
    mgr.set(setting1, setting1.default)
    assert(!triggered)
    // However, value is still updated
    assert(mgr.store.getString(setting1.id) === setting1.default)
  }
}
