import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

val prepareRepo = taskKey[Unit]("Create a local Maven repository with SkillsJars fixtures.")
val assertExtractedLayout = taskKey[Unit]("Assert that SkillsJars were extracted only for the root project.")

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

lazy val app = project
  .in(file("app"))
  .settings(
    scalaVersion := "3.8.4",
    libraryDependencies += "com.skillsjars" % "child-skill" % "1.0.0" % Skills
  )

lazy val root = project
  .in(file("."))
  .aggregate(app)
  .settings(
    scalaVersion := "3.8.4",
    libraryDependencies += "com.skillsjars" % "root-skill" % "1.0.0" % Skills,
    skillsJarsOutputDir := Some(file("output")),
    prepareRepo := {
      val repoRoot = Path.userHome / ".ivy2" / "local"
      IO.delete(repoRoot / "com.skillsjars" / "root-skill")
      IO.delete(repoRoot / "com.skillsjars" / "child-skill")
      IO.delete(baseDirectory.value / "output")

      publishModule(
        repoRoot,
        "com.skillsjars",
        "root-skill",
        "1.0.0",
        Seq(
          "META-INF/skills/acme/root/SKILL.md" -> "# Root Skill\n",
          "META-INF/skills/acme/root/docs/readme.md" -> "from root\n"
        )
      )

      publishModule(
        repoRoot,
        "com.skillsjars",
        "child-skill",
        "1.0.0",
        Seq(
          "META-INF/skills/acme/child/SKILL.md" -> "# Child Skill\n",
          "META-INF/skills/acme/child/docs/readme.md" -> "from child\n"
        )
      )
    },
    assertExtractedLayout := {
      val output = baseDirectory.value / "output"

      assert((output / "skillsjars__acme__root" / "SKILL.md").exists(), "root skill should be extracted")
      assert(
        IO.read(output / "skillsjars__acme__root" / "docs" / "readme.md").trim == "from root",
        "root skill contents should be extracted"
      )
      assert(
        !(output / "skillsjars__acme__child").exists(),
        "child project extraction should not run through root aggregation"
      )
    }
  )
