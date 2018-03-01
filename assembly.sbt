val buildOutputPath = file("./_build")

mainClass          in assembly := Some("org.fs.mael.MaelstromDownloaderMain")
assemblyJarName    in assembly := name.value + "-" + version.value + "b" + buildInfoBuildNumber.value + ".jar"
assemblyOutputPath in assembly := buildOutputPath / (assemblyJarName in assembly).value

// Discard META-INF files to avoid assembly deduplication errors
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

import SwtDependencies._

val swtOutputDir = buildOutputPath / "lib" / "swt"

val copySwtLibs = taskKey[Unit](s"Copies all OS-specific SWT jars to ${swtOutputDir}")

copySwtLibs := {
  val toCopy = new collection.mutable.HashSet[(File, File)]
  update.value.configuration(SwtConfig).get.retrieve { (conf: ConfigRef, mid: ModuleID, art: Artifact, cached: File) =>
    val fileName = mid.name + ".jar"
    toCopy += (cached -> swtOutputDir / fileName)
    cached
  }
  IO.delete(swtOutputDir)
  IO.copy(toCopy)
}
