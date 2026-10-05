enablePlugins(SbtPlugin)

name := "skillsjars-sbt-plugin"

organization := "com.skillsjars"

scalaVersion := "2.12.21"

crossScalaVersions := Seq("2.12.21", "3.8.4")

pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" => "1.12.12"
    case _      => "2.0.0"
  }
}

description := "sbt plugin for unpacking SkillsJars from Maven repositories"

homepage := Some(url("https://github.com/skillsjars/skillsjars-sbt-plugin"))

developers := List(
  Developer(
    "javierarrieta",
    "Javier Arrieta",
    "javierarrieta@users.noreply.github.com",
    url("https://github.com/javierarrieta")
  ),
  Developer(
    "jamesward",
    "James Ward",
    "james@jamesward.com",
    url("https://jamesward.com")
  )
)

licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))

ThisBuild / versionScheme := Some("semver-spec")

addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.2.0")

javacOptions ++= Seq("-source", "17", "-target", "17")
scalacOptions ++= (scalaBinaryVersion.value match {
  case "2.12" => Seq.empty // Scala 2.12 cannot target > JDK 8
  case _      => Seq("-release", "17")
})

scriptedLaunchOpts ++= Seq(
  "-Xmx1024M",
  s"-Dplugin.version=${version.value}"
)

scriptedBufferLog := false

// sbt-mcp (loopback-only: its tools can execute build tasks)
Global / mcpEnabled := true
Global / mcpHost := "127.0.0.1"
Global / mcpPort := 5113

// SkillsJars: extract agent Skills with `./sbt extractSkillsJars`
skillsJarsOutputDir := Some(file(".kiro/skills"))

libraryDependencies += "com.jamesward" % "skills" % "0.0.11" % Skills
