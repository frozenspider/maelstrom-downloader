package org.fs.mael.ui.components.proxy

import java.util.UUID

import org.eclipse.jface.preference._
import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.fs.mael.core.config.proxy.Proxy
import org.fs.mael.core.config.proxy.Proxy.NoProxy
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.ui.components.MUiComponent
import org.fs.mael.ui.utils.SwtUtils
import org.apache.http.conn.util.InetAddressUtils

/** Renders {@code Proxy} details, optionally allowing to edit it */
class ProxyEditorComponent(
  parent:     Composite,
  layoutData: Any,
  saveProxy:  Proxy => Unit
) extends MUiComponent[Composite](parent) {

  private val proxyNamesAndTypes: Seq[(String, Class[_])] = Seq(
    ("SOCKS5", classOf[Proxy.SOCKS5])
  )
  private var uuid: UUID = _
  /** Whether edit is allowed for this editor */
  private var editAllowed: Boolean = true
  /** Whether currently rendered proxy can technically be edited */
  private var editPossible: Boolean = _

  private var nameEditor: StringFieldEditor = _
  private var typeEditor: RadioGroupFieldEditor = _
  private var typeComposite: Composite = _
  private var hostEditor: StringFieldEditor = _
  private var portEditor: IntegerFieldEditor = _
  private var dnsEditor: BooleanFieldEditor = _
  private var usernameEditor: StringFieldEditor = _
  private var passwordEditor: StringFieldEditor = _
  private var dnsButton: Button = _
  private var editors: Seq[FieldEditor] = Seq.empty

  var parentPageOption: Option[PreferencePage] = None

  private val page = new ProxyFieldEditorPreferencePage()

  override val peer: Composite = {
    val peer = new Composite(parent, SWT.NONE)
    peer.setLayoutData(layoutData)
    peer.setLayout(new FillLayout)

    page.createControl(peer)
    peer.setVisible(false)
    peer
  }

  private def editable = this.editAllowed && this.editPossible

  private def typeButtons: IndexedSeq[Button] = {
    typeComposite.getChildren.collect {
      case btn: Button => btn
    }
  }

  def setEditAllowed(editAllowed: Boolean): Unit = {
    this.editAllowed = editAllowed
    SwtUtils.setEnabled(peer, parent, editable)
  }

  /**
   * Render a proxy in this editor, optionally specify explicitly whether it can be edited.
   * Note that predefined proxies won't be editable anyway.
   */
  def render(proxy: Proxy): Unit = {
    this.uuid = proxy.uuid
    this.editPossible = proxy.isInstanceOf[Proxy.CustomProxy]
    SwtUtils.setEnabled(peer, parent, editable)
    nameEditor.setStringValue(proxy.name)
    val typeIdx = proxyNamesAndTypes.indexWhere(_._2 == proxy.getClass)
    proxy match {
      case NoProxy =>
        typeButtons.foreach(_.setSelection(false))
        hostEditor.setStringValue("")
        portEditor.setStringValue("")
        usernameEditor.setStringValue("")
        passwordEditor.setStringValue("")
        dnsButton.setSelection(false)
        page.setValid(false)
        page.setErrorMessage(null)
      case proxy: Proxy.SOCKS5 =>
        typeButtons(typeIdx).setSelection(true)
        hostEditor.setStringValue(proxy.host)
        portEditor.setStringValue(proxy.port.toString)
        usernameEditor.setStringValue(proxy.authOption.map(_._1).getOrElse(""))
        passwordEditor.setStringValue(proxy.authOption.map(_._2).getOrElse(""))
        dnsButton.setSelection(proxy.dns)
        revalidate()
    }
    peer.setVisible(true)
  }

  /** Render a new non-existent proxy entry in this editor */
  def renderNew(uuid: UUID, name: String): Unit = {
    this.uuid = uuid
    this.editPossible = true
    SwtUtils.setEnabled(peer, parent, editable)
    typeButtons.foreach(_.setSelection(false))
    typeButtons(0).setSelection(true)
    nameEditor.setStringValue(name)
    hostEditor.setStringValue("localhost")
    portEditor.setStringValue("")
    usernameEditor.setStringValue("")
    passwordEditor.setStringValue("")
    dnsButton.setSelection(false)

    revalidate()
    peer.setVisible(true)
  }

  /** Compose a valid {@code Proxy} from this editor, or throw {@code IllegalStateException} */
  @throws[IllegalStateException]
  def value: Proxy = {
    if (!peer.isVisible) throw new IllegalStateException("Editor is hidden, this shouldn't be called! Its a software bug!")
    if (!revalidate()) throw new IllegalStateException("Invalid editor value(s), this shouldn't be called! Its a software bug!")
    val res = typeButtons.indexWhere(_.getSelection) match {
      case 0 =>
        new Proxy.SOCKS5(uuid, nameEditor.getStringValue, hostEditor.getStringValue, portEditor.getIntValue, {
          val (user, pass) = (usernameEditor.getStringValue, passwordEditor.getStringValue)
          if (!user.isEmpty || !pass.isEmpty)
            Some((user, pass))
          else
            None
        }, dnsButton.getSelection)
    }
    res
  }

  private def revalidate(): Boolean = {
    editors.forall { e =>
      // Method is protected so we have to use reflection
      try {
        val refreshValidState = e.getClass.getDeclaredMethod("refreshValidState")
        refreshValidState.setAccessible(true)
        refreshValidState.invoke(e)
      } catch {
        case e: NoSuchMethodException => // NOOP
      }
      e.isValid()
    }
    page.updateValidityFlag()
    page.isValid()
  }

  private class ProxyFieldEditorPreferencePage extends FieldEditorPreferencePage {
    val ValidHostnameRegex = """^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]*[A-Za-z0-9])$"""

    this.setContainer(new IPreferencePageContainer {
      def getPreferenceStore(): IPreferenceStore = null
      def updateButtons(): Unit = {}
      def updateMessage(): Unit = parentPageOption foreach { parentPage =>
        parentPage.setMessage(getMessage, getMessageType)
        parentPage.setErrorMessage(getErrorMessage)
        parentPage.getContainer.updateMessage()
      }
      def updateTitle(): Unit = {}
    })

    private def reg[FE <: FieldEditor](fe: FE): FE = {
      editors = editors :+ fe
      addField(fe)
      fe
    }

    override def createFieldEditors(): Unit = {
      nameEditor = reg(new StringFieldEditor("", "Proxy name:", getFieldEditorParent()))
      nameEditor.setEmptyStringAllowed(false)

      val typeParent = getFieldEditorParent()
      typeEditor = reg(new RadioGroupFieldEditor("", "Proxy type:", 1, proxyNamesAndTypes.map(nt => Array(nt._1, "")).toArray[Array[String]], typeParent))
      typeComposite = typeEditor.getRadioBoxControl(typeParent)

      hostEditor = reg(new StringFieldEditor("", "Host:", getFieldEditorParent()) {
        override def doCheckState(): Boolean = {
          val s = getStringValue()
          if (!InetAddressUtils.isIPv4Address(s)
            && !InetAddressUtils.isIPv6Address(s)
            && !s.matches(ValidHostnameRegex)) {
            setErrorMessage("Host is not a valid domain name or IP address")
          } else {
            setErrorMessage(null)
          }
          Option(getErrorMessage).isEmpty
        }
      })
      hostEditor.setEmptyStringAllowed(false)

      portEditor = reg(new IntegerFieldEditor("", "Port:", getFieldEditorParent()))
      portEditor.setValidRange(0, 65535)

      val authGroup = {
        val authParent = getFieldEditorParent()
        authParent.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false))
        authParent.setLayout(new FillLayout)
        new Group(authParent, SWT.NONE)
      }

      authGroup.setText("Auth, if necessary")

      usernameEditor = reg(new StringFieldEditor("", "Username:", authGroup))
      usernameEditor.setEmptyStringAllowed(true)
      passwordEditor = reg(new StringFieldEditor("", "Password:", authGroup))
      passwordEditor.setEmptyStringAllowed(true)

      authGroup.getLayout.asInstanceOf[GridLayout].withCode { gl =>
        gl.verticalSpacing = 3
        gl.marginTop = 3
        gl.marginBottom = 3
        gl.marginLeft = 3
        gl.marginRight = 3
      }

      val dnsParent = getFieldEditorParent()
      dnsEditor = reg(new BooleanFieldEditor("", "Use for DNS:", BooleanFieldEditor.SEPARATE_LABEL, dnsParent) {
        override def getChangeControl(parent: Composite): Button = super.getChangeControl(parent).withCode { button =>
          dnsButton = button
        }
      })
      val dnsToolip = "Use this proxy for DNS resolution as well"
      dnsEditor.getLabelControl(dnsParent).setToolTipText(dnsToolip)
    }

    override def performApply(): Unit = {
      super.performApply()
      if (revalidate()) saveProxy(value)
    }

    def updateValidityFlag(): Unit = {
      checkState()
    }
  }
}
