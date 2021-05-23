import sbt._

object SwtDependencies {

  /** Special configuration used for OS-specific SWT jars dependency management */
  lazy val SwtConfig = config("swt-config")

  val swtVer = "3.106.2"
  val jfaceVer = "3.13.2"
  val equinoxCommonVer = "3.10.0"
  val eclipseCommandsVer = "3.9.100"

  //
  // Artifact definitions
  //

  val swtOrganization = "org.eclipse.platform"
  val swtBaseArtifact = "org.eclipse.swt"
  val jfaceArtifact = "org.eclipse.jface"
  val equinoxCommonArtifact = "org.eclipse.equinox.common"
  val eclipseCommandsArtifact = "org.eclipse.core.commands"

  val swtOsArtifacts: Map[String, String] = Map(
    "win32" -> "win32.win32.x86",
    "win64" -> "win32.win32.x86_64",
    "linux" -> "gtk.linux.x86",
    // "mac32" -> "cocoa.macosx.x86",
    "mac64" -> "cocoa.macosx.x86_64"
  ).mapValues(swtBaseArtifact + "." + _)

  val swtCurrOsArtifact: String = (sys.props("os.name"), sys.props("os.arch")) match {
    case ("Linux", _)                              => swtOsArtifacts("linux")
    case ("Mac OS X", "amd64" | "x86_64")          => swtOsArtifacts("mac64")
    // case ("Mac OS X", _)                           => swtOsArtifacts("mac32")
    case (os, "amd64") if os.startsWith("Windows") => swtOsArtifacts("win64")
    case (os, _) if os.startsWith("Windows")       => swtOsArtifacts("win32")
    case (os, arch)                                => sys.error("Cannot obtain lib for OS '" + os + "' and architecture '" + arch + "'")
  }

  //
  // Dependency definitions
  //

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
