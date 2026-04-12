import com.skillsjars.sbt.SkillsJarsPlugin
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

val prepareConflictRepo = taskKey[Unit]("Create conflicting SkillsJars fixtures.")

def writeText(file: File, content: String): Unit = {
  IO.write(file, content, StandardCharsets.UTF_8, append = false)
}

def ivyContent(groupId: String, artifactId: String, version: String): String =
  s"""<ivy-module version="2.0">
     |  <info organisation="$groupId" module="$artifactId" revision="$version"/>
     |  <configurations>
     |    <conf name="default" visibility="public"/>
     |  </configurations>
     |  <publications>
     |    <artifact name="$artifactId" type="jar" ext="jar" conf="default"/>
     |  </publications>
     |</ivy-module>
     |""".stripMargin

def writeJar(jarFile: File, entries: Seq[(String, String)]): Unit = {
  IO.createDirectory(jarFile.getParentFile)
  val jar = new JarOutputStream(new FileOutputStream(jarFile))
  try {
    entries.foreach { case (path, content) =>
      jar.putNextEntry(new JarEntry(path))
      jar.write(content.getBytes(StandardCharsets.UTF_8))
      jar.closeEntry()
    }
  } finally {
    jar.close()
  }
}

def publishModule(repoRoot: File, groupId: String, artifactId: String, version: String, entries: Seq[(String, String)]): Unit = {
  val base = repoRoot / groupId / artifactId / version
  val jar = base / "jars" / s"$artifactId.jar"
  val ivy = base / "ivys" / "ivy.xml"
  writeJar(jar, entries)
  writeText(ivy, ivyContent(groupId, artifactId, version))
}

lazy val root = project
  .in(file("."))
  .enablePlugins(SkillsJarsPlugin)
  .settings(
    scalaVersion := "2.12.20",
    libraryDependencies ++= Seq(
      "com.skillsjars" % "conflict-one" % "1.0.0",
      "com.skillsjars" % "conflict-two" % "1.0.0"
    ),
    prepareConflictRepo := {
      val repoRoot = Path.userHome / ".ivy2" / "local"
      IO.delete(repoRoot / "com.skillsjars" / "conflict-one")
      IO.delete(repoRoot / "com.skillsjars" / "conflict-two")

      publishModule(
        repoRoot,
        "com.skillsjars",
        "conflict-one",
        "1.0.0",
        Seq(
          "META-INF/skills/team/shared/SKILL.md" -> "# Shared One\n",
          "META-INF/skills/team/shared/docs/readme.md" -> "from one\n"
        )
      )

      publishModule(
        repoRoot,
        "com.skillsjars",
        "conflict-two",
        "1.0.0",
        Seq(
          "META-INF/skills/team/shared/SKILL.md" -> "# Shared Two\n",
          "META-INF/skills/team/shared/docs/readme.md" -> "from two\n"
        )
      )
    }
  )
