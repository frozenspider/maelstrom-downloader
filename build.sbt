name           := "maelstrom-downloader"
val prettyName =  "Maelstrom Downloader"
version        := "0.1"
scalaVersion   := "2.12.3"

sourceManaged            := baseDirectory.value / "src_managed"
sourceManaged in Compile := baseDirectory.value / "src_managed" / "main" / "scala"
sourceManaged in Test    := baseDirectory.value / "src_managed" / "test" / "scala"

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      "prettyName" -> prettyName,
      version,
      "fullPrettyName" -> (prettyName + " v" + version.value),
      buildInfoBuildNumber),
    buildInfoPackage := "org.fs.mael"
  )

resolvers += "jitpack"  at "https://jitpack.io"
resolvers += "swt-repo" at "http://maven-eclipse.github.io/maven"

val swtArtifact = {
  val osDependentPart = (sys.props("os.name"), sys.props("os.arch")) match {
    case ("Linux", _) => "gtk.linux.x86"
    case ("Mac OS X", "amd64" | "x86_64") => "cocoa.macosx.x86_64"
    case ("Mac OS X", _) => "cocoa.macosx.x86"
    case (os, "amd64") if os.startsWith("Windows") => "win32.win32.x86_64"
    case (os, _) if os.startsWith("Windows") => "win32.win32.x86"
    case (os, arch) => sys.error("Cannot obtain lib for OS '" + os + "' and architecture '" + arch + "'")
  }
  "org.eclipse.swt." + osDependentPart
}
libraryDependencies ++= Seq(
  // UI
  "org.eclipse.swt"         % swtArtifact             % "4.6.1",
  // Logging
  "org.slf4s"               %% "slf4s-api"            % "1.7.25",
  "ch.qos.logback"          %  "logback-classic"      % "1.1.2",
  // Other
  "com.github.frozenspider" %% "fs-common-utils"      % "0.1.3",
  "org.apache.commons"      %  "commons-lang3"        % "3.4",
  "com.github.nscala-time"  %% "nscala-time"          % "2.16.0",
  "com.typesafe"            %  "config"               % "1.3.2",
  // Test
  "junit"                   %  "junit"                % "4.12"  % "test",
  "org.scalactic"           %% "scalactic"            % "3.0.4" % "test",
  "org.scalatest"           %% "scalatest"            % "3.0.4" % "test"
)
