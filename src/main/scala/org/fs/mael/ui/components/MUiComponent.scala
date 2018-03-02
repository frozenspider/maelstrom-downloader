package org.fs.mael.ui.components

import org.eclipse.swt.widgets.Composite

/**
 * Base class for Maelstrom UI component, encapsulating SWT peer
 *
 * @author FS
 */
abstract class MUiComponent[C <: Composite](protected val parent: Composite) {
  protected def display = parent.getDisplay

  def peer: C
}
