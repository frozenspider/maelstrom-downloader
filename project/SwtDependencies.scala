import sbt._

object SwtDependencies {

  /** Special configuration used for OS-specific SWT jars dependency management */
  lazy val SwtConfig = config("swt-config")

  val swtVer = "3.124.0"
  val jfaceVer = "3.30.0"
  val equinoxCommonVer = "3.18.0"
  val eclipseCommandsVer = "3.11.0"

  //
  // Artifact definitions
  //

  val swtBaseArtifact = "org.eclipse.swt"
  val jfaceArtifact = "org.eclipse.jface"
  val equinoxCommonArtifact = "org.eclipse.equinox.common"
  val eclipseCommandsArtifact = "org.eclipse.core.commands"

  // 32-bit Windows and MacOS are no longer supported
  val swtOsArtifacts: Map[(String, String), String] = Map(
    ("win",   "amd64")   -> "win32.win32.x86_64",
    ("linux", "amd64")   -> "gtk.linux.x86_64",
    ("linux", "aarch64") -> "gtk.linux.aarch64",
    ("macos", "amd64")   -> "cocoa.macosx.x86_64",
    ("macos", "aarch64") -> "cocoa.macosx.aarch64",
  ).mapValues(swtBaseArtifact + "." + _)


  val swtCurrOsArtifact: String = (sys.props("os.name"), sys.props("os.arch")) match {
    case ("Linux", arch)                        => swtOsArtifacts("linux" -> arch)
    case ("Mac OS X", arch)                     => swtOsArtifacts("macos" -> arch)
    case (os, arch) if os.startsWith("Windows") => swtOsArtifacts("win" -> arch)
    case (os, arch)                             => sys.error("Cannot obtain lib for OS '" + os + "' and architecture '" + arch + "'")
  }

  //
  // Dependency definitions
  //
  val swtOrganization = "org.eclipse.platform"

  val swtBaseDep =
    swtOrganization % swtBaseArtifact % swtVer exclude (swtOrganization, "org.eclipse.swt.${osgi.platform}")

  val swtCurrOsDep =
    swtOrganization % swtCurrOsArtifact % swtVer exclude (swtOrganization, swtBaseArtifact)

  val jfaceDep =
    (swtOrganization % jfaceArtifact % jfaceVer)
      .exclude(swtOrganization, swtBaseArtifact)
      .exclude(swtOrganization, equinoxCommonArtifact)
      .exclude(swtOrganization, eclipseCommandsArtifact)

  val equinoxCommonDep =
    swtOrganization % equinoxCommonArtifact % equinoxCommonVer

  val eclipseCommandsDep =
    swtOrganization % eclipseCommandsArtifact % eclipseCommandsVer exclude (swtOrganization, equinoxCommonArtifact)

  /** Return all OS-specific SWT libs as modules for specifig configuration */
  def getSwtOsDeps(config: Configuration): Seq[ModuleID] = swtOsArtifacts.values.map(artifact =>
    swtOrganization % artifact % swtVer % config exclude (swtOrganization, swtBaseArtifact)).toSeq

  val swtDeps = (Seq(swtBaseDep, swtCurrOsDep, jfaceDep, equinoxCommonDep, eclipseCommandsDep)
    ++ getSwtOsDeps(SwtConfig))
}
