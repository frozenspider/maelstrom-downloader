import sbt._

object SwtDependencies {
  val swtVer = "3.106.2"

  //
  // Artifact definitions
  //

  val swtBaseArtifact = "org.eclipse.swt"

  val swtOsArtifacts = Map(
    "win32" -> "win32.win32.x86",
    "win64" -> "win32.win32.x86_64",
    "linux" -> "gtk.linux.x86",
    "mac32" -> "cocoa.macosx.x86",
    "mac64" -> "cocoa.macosx.x86_64"
  ).mapValues(swtBaseArtifact + "." + _)

  //
  // Dependency definitions
  //

  val swtBaseDep =
    "org.eclipse.platform" % swtBaseArtifact % swtVer exclude ("org.eclipse.platform", "org.eclipse.swt.${osgi.platform}")

  val swtOsDeps = swtOsArtifacts.mapValues(artifact =>
    "org.eclipse.platform" % artifact % swtVer % "provided" exclude ("org.eclipse.platform", swtBaseArtifact))

  val swtCurrOsDep = (sys.props("os.name"), sys.props("os.arch")) match {
    case ("Linux", _)                              => swtOsDeps("linux")
    case ("Mac OS X", "amd64" | "x86_64")          => swtOsDeps("mac64")
    case ("Mac OS X", _)                           => swtOsDeps("mac32")
    case (os, "amd64") if os.startsWith("Windows") => swtOsDeps("win64")
    case (os, _) if os.startsWith("Windows")       => swtOsDeps("win32")
    case (os, arch)                                => sys.error("Cannot obtain lib for OS '" + os + "' and architecture '" + arch + "'")
  }
}
