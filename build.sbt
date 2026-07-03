enablePlugins(SbtPlugin)

val Sbt1Version = "1.12.12"
val Sbt2Version = "2.0.1"

name := "skillsjars-sbt-plugin"

organization := "com.skillsjars"

scalaVersion := "2.12.21"

crossScalaVersions := Seq("2.12.21", "3.8.4")

pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" => Sbt1Version
    case _      => Sbt2Version
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

addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0")

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
