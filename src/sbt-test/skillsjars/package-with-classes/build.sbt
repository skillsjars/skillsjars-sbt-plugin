val assertProjectAndSkillsPackaged = taskKey[Unit]("Assert that compiled classes and packaged skills coexist in the jar.")

lazy val root = project
  .in(file("."))
  .settings(
    scalaVersion := "2.12.20",
    organization := "com.example.test",
    scmInfo := Some(ScmInfo(url("https://github.com/testorg/testrepo"), "scm:git:https://github.com/testorg/testrepo.git")),
    assertProjectAndSkillsPackaged := {
      val jarFile = (Compile / packageBin).value
      val jar = new java.util.jar.JarFile(jarFile)
      val entries =
        try scala.collection.JavaConverters.enumerationAsScalaIteratorConverter(jar.entries()).asScala.map(_.getName).toSet
        finally jar.close()

      assert(
        entries.contains("example/Greeter.class"),
        "project Scala classes should be packaged"
      )
      assert(
        entries.contains("META-INF/skills/testorg/testrepo/helper-skill/SKILL.md"),
        "skills should be packaged alongside project classes"
      )
      assert(
        entries.contains("META-INF/skills/testorg/testrepo/helper-skill/prompts/system.txt"),
        "nested skill files should be packaged alongside project classes"
      )
    }
  )
