package org.fs.mael.ui.components

import scala.concurrent.Future
import scala.concurrent.Promise

import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.utils.SwtUtils._
import org.slf4s.Logging

abstract class PopupEditorDialog[RT](title: String, parent: Shell, initial: RT) { this: Logging =>

  private var result: Promise[Option[RT]] = Promise()

  protected var shell: Shell = _
  protected var scrollpane: ScrolledComposite = _
  protected var dataPane: Composite = _

  def prompt(): Future[Option[RT]] = {
    require(!result.isCompleted, "Duplicate call to prompt")
    init()
    shell.open()
    result.future
  }

  /** Perform initialization. Done here instead of constructor to work nicely with child fields */
  protected def init(): Unit = {
    shell = new Shell(parent, SWT.SHELL_TRIM | SWT.BORDER | SWT.APPLICATION_MODAL)
    shell.setMinimumSize(550, 100)
    shell.setImage(getImageIcon(parent))
    shell.setText(title)
    shell.setLayout(new GridLayout())

    initBeforeMainArea()

    scrollpane = new ScrolledComposite(shell, SWT.H_SCROLL | SWT.V_SCROLL)
    scrollpane.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    scrollpane.setExpandHorizontal(true)
    scrollpane.setExpandVertical(true)
    dataPane = new Composite(scrollpane, SWT.NONE)
    dataPane.setLayout(new GridLayout(3, false).withCode { layout =>
      layout.horizontalSpacing = 1
      layout.verticalSpacing = 1
    })
    scrollpane.setContent(dataPane)

    renderContent(initial)

    fillBottomButtons()

    shell.pack()
    centerOnScreen(shell)
  }

  /** Recursively get image icon for first parent of the given component that has it */
  private def getImageIcon(parent: Composite): Image = parent match {
    case shell: Shell if shell.getImage != null => shell.getImage
    case null                                   => null
    case other                                  => getImageIcon(other.getParent)
  }

  protected def updateScroll(): Unit = {
    scrollpane.setMinSize(dataPane.computeSize(SWT.DEFAULT, SWT.DEFAULT))
    dataPane.layout()
  }

  private def fillBottomButtons(): Unit = {
    val bottomButtonRow = new Composite(shell, SWT.NONE).withCode { composite =>
      composite.setLayout(new RowLayout().withCode { layout =>
        layout.marginTop = 0
        layout.marginBottom = 0
      })
      composite.setLayoutData(new GridData(SWT.CENTER, SWT.TRAIL, true, false))
    }

    fillBottomButtonsInto(bottomButtonRow)
  }

  protected def fillBottomButtonsInto(bottomButtonRow: Composite): Unit = {
    val bottomButtonRow = new Composite(shell, SWT.NONE).withCode { composite =>
      composite.setLayout(new RowLayout().withCode { layout =>
        layout.marginTop = 0
        layout.marginBottom = 0
      })
      composite.setLayoutData(new GridData(SWT.CENTER, SWT.TRAIL, true, false))
    }

    val okButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&OK")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => okClicked())
      shell.setDefaultButton(btn)
    }

    val cancelButton = new Button(bottomButtonRow, SWT.PUSH).withCode { btn =>
      btn.setText("&Cancel")
      btn.setLayoutData(new RowData(100, SWT.DEFAULT))
      btn.addListener(SWT.Selection, e => cancelClicked())
    }
  }

  protected def okClicked(): Unit = {
    tryShowingError(shell, log) {
      val resultValue = getResultValue()
      result.success(Some(resultValue))
      shell.dispose()
    }
  }

  protected def cancelClicked() = {
    result.success(None)
    shell.dispose()
  }

  protected def initBeforeMainArea(): Unit

  protected def renderContent(content: RT): Unit

  /** Get a result value from this editor, throwing UserFriendlyException if validation fails */
  protected def getResultValue(): RT
}
