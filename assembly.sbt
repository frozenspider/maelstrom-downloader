//
// sbt-assembly configuration
//

val buildOutputPath = file("./_build")

assembly / mainClass          := Some("org.fs.mael.MaelstromDownloaderMain")
assembly / assemblyJarName    := name.value + ".jar"
assembly / assemblyOutputPath := buildOutputPath / (assembly / assemblyJarName).value

// Discard META-INF files to avoid assembly deduplication errors
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}


//
// copySwtLibs task
//

import SwtDependencies._

val swtLibRelativeOutputDirString = "lib/swt"
val swtLibOutputDir = buildOutputPath / swtLibRelativeOutputDirString

val copySwtLibs = taskKey[Unit](s"Copies all OS-specific SWT jars to ${swtLibOutputDir}")

copySwtLibs := {
  val toCopy = new collection.mutable.HashSet[(File, File)]
  update.value.configuration(SwtConfig).get.retrieve { (conf: ConfigRef, mid: ModuleID, art: Artifact, cached: File) =>
    val fileName = mid.name + ".jar"
    toCopy += (cached -> swtLibOutputDir / fileName)
    cached
  }
  IO.delete(swtLibOutputDir)
  IO.copy(toCopy)
}


//
// launch4j task
//

val launch4j = taskKey[Unit](s"Generates Launch4j executable binaries")

launch4j := Def.taskDyn {
  import net.sf.launch4j.{ Log, Builder }
  import net.sf.launch4j.config._

  val log = streams.value.log

  if (!sys.props("os.name").toLowerCase.startsWith("windows")) Def.task {
    log.warn("Not a Windows OS, can't assemble .exe files")
  } else Def.task {
    val launch4jBasedirString = sys.env.getOrElse("LAUNCH4J_HOME",
      throw new Exception("Please install Launch4j (preferribly v3.11) locally and set LAUNCH4J_HOME env variable") with FeedbackProvidedException)
    val launch4jBasedir = new File(launch4jBasedirString)

    val configurations = Seq(
      ("x64", "org.eclipse.swt.win32.win32.x86_64.jar", Jre.RUNTIME_BITS_64)
    )

    ConfigPersister.getInstance.createBlank()
    val conf: Config = ConfigPersister.getInstance.getConfig
    conf.setHeaderType("gui")
    conf.setIcon(file("./src/main/resources/icons/main.ico"))
    conf.setJar(new File((assembly / assemblyJarName).value))
    conf.setDontWrapJar(true)
    conf.setDownloadUrl("http://java.com/download")

    val cp = new ClassPath
    cp.setMainClass((assembly / mainClass).value.get)
    conf.setClassPath(cp)

    val jre = new Jre
    jre.setMinVersion("1.8.0")
    jre.setJdkPreference(Jre.JDK_PREFERENCE_PREFER_JDK)
    conf.setJre(jre)

    configurations.foreach {
      case (arch, jarName, jreRuntimeBits) =>
        conf.setOutfile(buildOutputPath / s"${name.value}_${arch}.exe")

        val cpList = new java.util.ArrayList[String]
        cpList.add(swtLibRelativeOutputDirString + "/" + jarName)
        cp.setPaths(cpList)

        jre.setRuntimeBits(jreRuntimeBits)

        conf.validate()
        new Builder(Log.getConsoleLog, launch4jBasedir).build()
    }
  }
}


//
// shellScript task
//

val shellScript = taskKey[Unit](s"Generates a Linux/MacOS runnable shell script")

shellScript := {
  val file = buildOutputPath / (name.value + ".sh")
  // Added all artifacts to classpath - but not sure if that's really needed, seem to work without it.
  val nixArtifacts = swtOsArtifacts.toSeq.collect {
    case (("linux", _), artifact) => artifact
    case (("macos", _), artifact) => artifact
  }
  val cp = ((assembly / assemblyJarName).value +: nixArtifacts).mkString(":")
  val command = s"java -XstartOnFirstThread -cp '$cp' ${(assembly / mainClass).value.get}"
  IO.write(file, command + "\n")
}


//
// buildDistr task
//

val buildDistr = taskKey[Unit](s"Complete build: assemble a runnable .jar, copy SWT libs, generate Windows executables and Linux shell script")

buildDistr := {
  assembly.value
  launch4j.value
  shellScript.value
  copySwtLibs.value
}
