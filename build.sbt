name           := "maelstrom-downloader"
val prettyName =  "Maelstrom Downloader"
version        := "0.1.1-SNAPSHOT"
scalaVersion   := "2.12.3"

sourceManaged            := baseDirectory.value / "src_managed"
sourceManaged in Compile := baseDirectory.value / "src_managed" / "main" / "scala"
sourceManaged in Test    := baseDirectory.value / "src_managed" / "test" / "scala"

import SwtDependencies._

lazy val root = (project in file("."))
  .configs(SwtConfig)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      "prettyName" -> prettyName,
      version,
      "fullPrettyName" -> (prettyName + " v" + version.value),
    ),
    buildInfoOptions ++= Seq(
      BuildInfoOption.BuildTime
    ),
    buildInfoPackage := "org.fs.mael",
    buildInfoUsePackageAsPath := true
  )

resolvers += "jitpack"  at "https://jitpack.io"

libraryDependencies ++=
// Intricate SWT dependencies
Seq(swtBaseDep, swtCurrOsDep, jfaceDep) ++ getSwtOsDeps(SwtConfig) ++ 
// Regular dependencies
Seq(
  // Network
  "org.apache.httpcomponents" %  "httpclient"           % "4.5.5",
  // Logging
  "org.slf4s"                 %% "slf4s-api"            % "1.7.25",
  "ch.qos.logback"            %  "logback-classic"      % "1.1.2",
  // Other
  "com.github.frozenspider"   %% "fs-common-utils"      % "0.1.3",
  "org.apache.commons"        %  "commons-lang3"        % "3.4",
  "com.github.nscala-time"    %% "nscala-time"          % "2.16.0",
  "org.json4s"                %% "json4s-jackson"       % "3.5.3",
  "org.json4s"                %% "json4s-ext"           % "3.5.3",
  "com.typesafe"              %  "config"               % "1.3.2",
  // Test
  "junit"                     %  "junit"                % "4.12"  % "test",
  "org.scalactic"             %% "scalactic"            % "3.0.4" % "test",
  "org.scalatest"             %% "scalatest"            % "3.0.4" % "test"
) ++ getSwtOsDeps(SwtConfig)
