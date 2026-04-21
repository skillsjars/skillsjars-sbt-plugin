enablePlugins(SbtPlugin)

name := "skillsjars-sbt-plugin"

organization := "com.skillsjars"

scalaVersion := "2.12.20"

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

scriptedLaunchOpts ++= Seq(
  "-Xmx1024M",
  s"-Dplugin.version=${version.value}"
)

scriptedBufferLog := false
