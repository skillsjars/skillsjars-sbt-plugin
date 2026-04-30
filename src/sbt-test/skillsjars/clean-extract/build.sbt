import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

val prepareRepo = taskKey[Unit]("Create a local Maven repository with SkillsJars fixtures.")
val assertExtractedLayout = taskKey[Unit]("Assert that SkillsJars were extracted as expected.")
val assertCleanedLayout = taskKey[Unit]("Assert that sbt clean removes extracted SkillsJars output.")

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
  .settings(
    scalaVersion := "2.12.20",
    libraryDependencies += "com.skillsjars" % "catalog-skill" % "1.0.0" % Skills,
    skillsJarsOutputDir := Some(file("output")),
    prepareRepo := {
      val repoRoot = Path.userHome / ".ivy2" / "local"
      IO.delete(repoRoot / "com.skillsjars" / "catalog-skill")
      IO.delete(baseDirectory.value / "output")

      publishModule(
        repoRoot,
        "com.skillsjars",
        "catalog-skill",
        "1.0.0",
        Seq(
          "META-INF/skills/acme/catalog/SKILL.md" -> "# Catalog Skill\n",
          "META-INF/skills/acme/catalog/templates/query.sql" -> "select 1;\n"
        )
      )
    },
    assertExtractedLayout := {
      val output = baseDirectory.value / "output"

      assert((output / "skillsjars__acme__catalog" / "SKILL.md").exists(), "catalog SKILL.md should exist")
      assert(
        IO.read(output / "skillsjars__acme__catalog" / "templates" / "query.sql").trim == "select 1;",
        "catalog skill contents should be extracted"
      )
    },
    assertCleanedLayout := {
      val output = baseDirectory.value / "output"

      assert(!output.exists(), "output directory should be removed by clean")
    }
  )
