lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "skillsjars-sbt-plugin",
    organization := "com.skillsjars",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.12.20",
    description := "sbt plugin for unpacking SkillsJars from Maven repositories",
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")),
    scriptedLaunchOpts ++= Seq(
      "-Xmx1024M",
      s"-Dplugin.version=${version.value}"
    ),
    scriptedBufferLog := false
  )
