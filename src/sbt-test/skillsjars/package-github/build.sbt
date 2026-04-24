val assertPackagedLayout = taskKey[Unit]("Assert that skills were packaged into managed resources.")
val assertCompiledLayout = taskKey[Unit]("Assert that packaged skills are present in the packaged jar.")
val seedStalePackagedSkills = taskKey[Unit]("Seed stale packaged skills under an old prefix.")

lazy val root = project
  .in(file("."))
  .settings(
    scalaVersion := "2.12.20",
    organization := "com.example.test",
    scmInfo := Some(ScmInfo(url("https://github.com/testorg/testrepo"), "scm:git:https://github.com/testorg/testrepo.git")),
    skillsJarsAllowedTools := Map(
      "test-skill" -> "Bash Read Edit"
    ),
    seedStalePackagedSkills := {
      val staleFile =
        (Compile / resourceManaged).value / "META-INF" / "skills" / "com" / "example" / "test" / "stale-skill" / "stale.txt"
      IO.write(staleFile, "stale", java.nio.charset.StandardCharsets.UTF_8, append = false)
    },
    assertPackagedLayout := {
      val packaged = packageSkillsJars.value
      val resourceRoot = (Compile / resourceManaged).value.getCanonicalFile
      val packagedPaths = packaged.map(_.getCanonicalPath.replace('\\', '/')).toSet

      assert(
        packagedPaths.exists(_.endsWith("/META-INF/skills/testorg/testrepo/test-skill/SKILL.md")),
        s"test-skill SKILL.md should exist\n${packagedPaths.toSeq.sorted.mkString("\n")}"
      )
      assert(
        packagedPaths.exists(_.endsWith("/META-INF/skills/testorg/testrepo/test-skill/nested/prompt.txt")),
        "nested skill content should be copied"
      )
      assert(
        packagedPaths.exists(_.endsWith("/META-INF/skills/testorg/testrepo/second-skill/SKILL.md")),
        "second skill SKILL.md should exist"
      )
      assert(
        packagedPaths.exists(_.endsWith("/META-INF/skills/testorg/testrepo/second-skill/docs/usage.md")),
        "second skill docs should be copied"
      )
      assert(
        IO.read(resourceRoot / "META-INF" / "skills" / "testorg" / "testrepo" / "test-skill" / "nested" / "prompt.txt").trim == "hello github",
        "nested skill content should match"
      )
      assert(
        IO.read(resourceRoot / "META-INF" / "skills" / "testorg" / "testrepo" / "second-skill" / "docs" / "usage.md").trim == "second docs",
        "second skill docs content should match"
      )
      assert(
        !(resourceRoot / "META-INF" / "skills" / "com" / "example" / "test" / "stale-skill" / "stale.txt").exists(),
        "stale packaged content under an old prefix should be removed"
      )
      assert(!packagedPaths.exists(_.contains("/ignored-dir/")), "directories without SKILL.md should be skipped")
    },
    assertCompiledLayout := {
      val jarFile = (Compile / packageBin).value
      val jar = new java.util.jar.JarFile(jarFile)
      val compiledPaths =
        try scala.collection.JavaConverters.enumerationAsScalaIteratorConverter(jar.entries()).asScala.map(_.getName).toSet
        finally jar.close()

      assert(
        compiledPaths.contains("META-INF/skills/testorg/testrepo/test-skill/SKILL.md"),
        "packaged jar should include test-skill"
      )
      assert(
        compiledPaths.contains("META-INF/skills/testorg/testrepo/second-skill/docs/usage.md"),
        "packaged jar should include second-skill"
      )
    }
  )
