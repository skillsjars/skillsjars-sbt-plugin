val assertPackagedLayout = taskKey[Unit]("Assert that skills were packaged using the organization path.")

lazy val root = project
  .in(file("."))
  .settings(
    scalaVersion := "2.12.20",
    organization := "com.example.test",
    assertPackagedLayout := {
      val packaged = packageSkillsJars.value
      val resourceRoot = (Compile / resourceManaged).value.getCanonicalFile
      val packagedPaths = packaged.map(_.getCanonicalPath.replace('\\', '/')).toSet

      assert(
        packagedPaths.exists(_.endsWith("/META-INF/skills/com/example/test/my-skill/SKILL.md")),
        "groupId-based SKILL.md should exist"
      )
      assert(
        IO.read(resourceRoot / "META-INF" / "skills" / "com" / "example" / "test" / "my-skill" / "data.txt").trim == "fallback path",
        "groupId-based content should be copied"
      )
    }
  )
