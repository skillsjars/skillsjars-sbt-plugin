import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

val prepareRepo = taskKey[Unit]("Create a local Maven repository with SkillsJars fixtures.")
val assertExtractedLayout = taskKey[Unit]("Assert that every project's SkillsJars were extracted, once each.")
val assertFiles = taskKey[Unit]("Assert that skillsJarsFiles lists what extractSkillsJars extracts.")

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

def skill(name: String, text: String): Seq[(String, String)] =
  Seq(
    s"META-INF/skills/acme/$name/SKILL.md" -> s"# ${name.capitalize} Skill\n",
    s"META-INF/skills/acme/$name/docs/readme.md" -> s"$text\n"
  )

// Aggregated by root.
lazy val app = project
  .in(file("app"))
  .settings(
    scalaVersion := "2.12.21",
    libraryDependencies ++= Seq(
      "com.skillsjars" % "child-skill" % "1.0.0" % Skills,
      "com.skillsjars" % "shared-skill" % "2.0.0" % Skills,
      // On the classpath only: not this project's SkillsJars.
      "com.skillsjars" % "classpath-skill" % "1.0.0"
    )
  )

// Not aggregated, and scanning its Compile classpath too.
lazy val standalone = project
  .in(file("standalone"))
  .settings(
    scalaVersion := "2.12.21",
    skillsJarsConfigurations := Seq(Skills, Compile),
    libraryDependencies += "com.skillsjars" % "compile-skill" % "1.0.0"
  )

// Another build.
lazy val other = RootProject(file("other"))

lazy val root = project
  .in(file("."))
  .aggregate(app)
  .dependsOn(other)
  .settings(
    scalaVersion := "2.12.21",
    libraryDependencies ++= Seq(
      "com.skillsjars" % "root-skill" % "1.0.0" % Skills,
      "com.skillsjars" % "shared-skill" % "1.0.0" % Skills
    ),
    skillsJarsOutputDir := Some(file("output")),
    prepareRepo := {
      val repoRoot = Path.userHome / ".ivy2" / "local"
      Seq("root-skill", "child-skill", "shared-skill", "classpath-skill", "compile-skill", "other-skill")
        .foreach(name => IO.delete(repoRoot / "com.skillsjars" / name))
      IO.delete(baseDirectory.value / "output")

      publishModule(repoRoot, "com.skillsjars", "root-skill", "1.0.0", skill("root", "from root"))
      publishModule(repoRoot, "com.skillsjars", "child-skill", "1.0.0", skill("child", "from child"))
      publishModule(repoRoot, "com.skillsjars", "shared-skill", "1.0.0", skill("shared", "shared 1.0.0"))
      publishModule(repoRoot, "com.skillsjars", "shared-skill", "2.0.0", skill("shared", "shared 2.0.0"))
      publishModule(repoRoot, "com.skillsjars", "classpath-skill", "1.0.0", skill("classpath", "from the classpath"))
      publishModule(repoRoot, "com.skillsjars", "compile-skill", "1.0.0", skill("compile", "from standalone's Compile"))
      publishModule(repoRoot, "com.skillsjars", "other-skill", "1.0.0", skill("other", "from the other build"))
    },
    assertExtractedLayout := {
      val output = baseDirectory.value / "output"
      def readme(name: String): String = IO.read(output / s"skillsjars__acme__$name" / "docs" / "readme.md").trim

      assert(readme("root") == "from root", "the root project's skill should be extracted")
      assert(readme("child") == "from child", "an aggregated project's skill should be extracted")
      assert(readme("compile") == "from standalone's Compile", "a project's skillsJarsConfigurations should apply to it")
      assert(readme("other") == "from the other build", "another build's skill should be extracted")
      assert(readme("shared") == "shared 2.0.0", "the highest revision of a SkillsJar should win")
      assert(!(output / "skillsjars__acme__classpath").exists(), "a classpath-only SkillsJar should not be extracted")
    },
    assertFiles := {
      val names = skillsJarsFiles.value.map(_.getName)
      Seq("root-skill.jar", "child-skill.jar", "compile-skill.jar", "other-skill.jar", "shared-skill.jar").foreach { expected =>
        assert(names.contains(expected), s"skillsJarsFiles should contain $expected: $names")
      }
      assert(!names.contains("classpath-skill.jar"), s"skillsJarsFiles should not contain classpath-skill.jar: $names")
      val shared = skillsJarsFiles.value.filter(_.getName == "shared-skill.jar")
      assert(shared.size == 1 && shared.head.getPath.contains("2.0.0"), s"skillsJarsFiles should have shared-skill 2.0.0 only: $shared")
    }
  )
