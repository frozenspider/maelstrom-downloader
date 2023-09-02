name           := "maelstrom-downloader"
val prettyName =  "Maelstrom Downloader"
version        := "1.2"
homepage       := Some(url("https://github.com/frozenspider/maelstrom-downloader"))
scalaVersion   := "2.13.10"

// Show tests duration and full stacktrace on test errors
Test / testOptions += Tests.Argument("-oDF")

// Disable concurrent test execution
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

Compile / sourceManaged := baseDirectory.value / "src_managed" / "main" / "scala"
Test    / sourceManaged := baseDirectory.value / "src_managed" / "test" / "scala"

import SwtDependencies._

lazy val root = (project in file("."))
  .configs(SwtConfig)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      "prettyName" -> prettyName,
      version,
      homepage,
      "fullPrettyName" -> (prettyName + " v" + version.value)
    ),
    buildInfoOptions ++= Seq(
      BuildInfoOption.BuildTime
    ),
    buildInfoPackage := "org.fs.mael",
    buildInfoUsePackageAsPath := true
  )

resolvers += "jitpack"  at "https://jitpack.io"

// Intricate SWT dependencies
libraryDependencies ++= swtDeps

// Regular dependencies
libraryDependencies ++= Seq(
  // Network
  "org.apache.httpcomponents" %  "httpclient"               % "4.5.5",
  // Logging
  "ch.timo-schmid"            %% "slf4s-api"                % "1.7.30.2",
  "org.slf4j"                 %  "jcl-over-slf4j"           % "1.7.36",
  "ch.qos.logback"            %  "logback-classic"          % "1.1.2",
  // Utility
  "com.github.frozenspider"   %% "fs-common-utils"          % "0.2.0",
  "commons-codec"             %  "commons-codec"            % "1.16.0",
  "org.apache.commons"        %  "commons-lang3"            % "3.12.0",
  "com.github.nscala-time"    %% "nscala-time"              % "2.32.0",
  // Other
  "org.json4s"                %% "json4s-jackson"           % "4.1.0-M2",
  "org.json4s"                %% "json4s-ext"               % "4.1.0-M2",
  "com.typesafe"              %  "config"                   % "1.4.2",
  "org.scala-lang.modules"    %% "scala-parser-combinators" % "2.3.0",
  // Test
  "junit"                     %  "junit"                    % "4.12"     % "test",
  "org.scalactic"             %% "scalactic"                % "3.2.15"   % "test",
  "org.scalatest"             %% "scalatest"                % "3.2.15"   % "test",
  "org.scalatestplus"         %% "junit-4-13"               % "3.2.15.0" % "test",
  "com.google.jimfs"          %  "jimfs"                    % "1.1"      % "test"
)
