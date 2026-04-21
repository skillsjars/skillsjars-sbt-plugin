import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

val prepareRepo = taskKey[Unit]("Create a local Maven repository with a non-com.skillsjars SkillsJar.")
val assertExtractedLayout = taskKey[Unit]("Assert that the non-com.skillsjars SkillsJar was extracted.")

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
  IO.write(ivy, ivyContent(groupId, artifactId, version), StandardCharsets.UTF_8, append = false)
}

lazy val root = project
  .in(file("."))
  .settings(
    scalaVersion := "2.12.20",
    libraryDependencies += "com.jamesward" % "skills" % "0.0.1" % Skills,
    skillsJarsOutputDir := Some(file("output")),
    prepareRepo := {
      val repoRoot = Path.userHome / ".ivy2" / "local"
      IO.delete(repoRoot / "com.jamesward" / "skills")
      IO.delete(baseDirectory.value / "output")

      publishModule(
        repoRoot,
        "com.jamesward",
        "skills",
        "0.0.1",
        Seq(
          "META-INF/skills/jamesward/demo/SKILL.md" -> "# Demo Skill\n",
          "META-INF/skills/jamesward/demo/prompt.txt" -> "hello world\n"
        )
      )
    },
    assertExtractedLayout := {
      val output = baseDirectory.value / "output"
      assert((output / "skillsjars__jamesward__demo" / "SKILL.md").exists(), "demo SKILL.md should exist")
      assert(
        IO.read(output / "skillsjars__jamesward__demo" / "prompt.txt").trim == "hello world",
        "demo skill contents should be extracted"
      )
    }
  )
