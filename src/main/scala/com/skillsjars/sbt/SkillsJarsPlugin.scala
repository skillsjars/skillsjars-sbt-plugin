package com.skillsjars.sbt

import sbt.Keys.baseDirectory
import sbt.Keys.clean
import sbt.Keys.ivyConfigurations
import sbt.Keys.organization
import sbt.Keys.resourceGenerators
import sbt.Keys.resourceManaged
import sbt.Keys.scmInfo
import sbt.Keys.streams
import sbt.Keys.updateFull
import sbt.Configuration
import sbt.Def
import sbt.File
import sbt.IO
import sbt.InputKey
import sbt.Plugins
import sbt.SettingKey
import sbt.AutoPlugin
import sbt.Compile
import sbt.config
import sbt.file
import sbt.plugins.JvmPlugin
import sbt.TaskKey
import sbt.complete.DefaultParsers.spaceDelimited

object SkillsJarsPlugin extends AutoPlugin {
  object autoImport {
    val Skills: Configuration = config("skills")

    val skillsJarsOutputDir: SettingKey[Option[File]] =
      sbt.settingKey[Option[File]]("Default output directory for extracted SkillsJars.")

    val skillsJarsConfigurations: SettingKey[Seq[Configuration]] =
      sbt.settingKey[Seq[Configuration]](
        "Configurations to scan for SkillsJars dependencies. Defaults to Seq(Skills)."
      )

    val skillsJarsSourceDir: SettingKey[File] =
      sbt.settingKey[File]("Directory containing local skills to package into the project resources.")

    val skillsJarsAllowedTools: SettingKey[Map[String, String]] =
      sbt.settingKey[Map[String, String]](
        "Allowed-tools declarations keyed by skill name for validating local SKILL.md frontmatter."
      )

    val extractSkillsJars: InputKey[File] =
      sbt.inputKey[File](
        "Extract SkillsJars into a target directory. Pass a directory argument or set skillsJarsOutputDir."
      )

    val packageSkillsJars: TaskKey[Seq[File]] =
      sbt.taskKey[Seq[File]](
        "Package local skills into managed resources under META-INF/skills."
      )
  }

  import autoImport._

  override def requires: Plugins = JvmPlugin
  override def trigger = allRequirements

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    ivyConfigurations += Skills,
    skillsJarsOutputDir := None,
    skillsJarsConfigurations := Seq(Skills),
    skillsJarsSourceDir := new File(baseDirectory.value, "skills"),
    skillsJarsAllowedTools := Map.empty,
    clean := clean
      .dependsOn(Def.task {
        skillsJarsOutputDir.value.toSeq.foreach { path =>
          val resolved = if (path.isAbsolute) path else new File(baseDirectory.value, path.getPath)
          IO.delete(resolved)
        }
      })
      .value,
    extractSkillsJars := {
      val outputArg = spaceDelimited("<dir>").parsed.headOption
      val outputDir = resolveOutputDir(outputArg, skillsJarsOutputDir.value, baseDirectory.value)
      val configurationNames = skillsJarsConfigurations.value.map(_.name).toSet

      SkillsJarsExtractor.extract(
        report = updateFull.value,
        outputPath = outputDir.toPath,
        configurationNames = configurationNames,
        log = streams.value.log
      )

      outputDir
    },
    Compile / packageSkillsJars := {
      SkillsJarsPackager.packageSkills(
        skillsDir = skillsJarsSourceDir.value.toPath,
        outputRoot = (Compile / resourceManaged).value.toPath,
        projectOrganization = organization.value,
        scm = scmInfo.value,
        allowedToolsBySkill = skillsJarsAllowedTools.value,
        log = streams.value.log
      )
    },
    packageSkillsJars := (Compile / packageSkillsJars).value,
    Compile / resourceGenerators += (Compile / packageSkillsJars).taskValue
  )

  private def resolveOutputDir(
      outputArg: Option[String],
      configuredOutput: Option[File],
      baseDir: File
  ): File =
    outputArg
      .filter(_.trim.nonEmpty)
      .map(file)
      .orElse(configuredOutput)
      .map { path =>
        if (path.isAbsolute) path else new File(baseDir, path.getPath)
      }
      .getOrElse(sys.error("An output directory is required. Use `extractSkillsJars <dir>` or set `skillsJarsOutputDir`."))
}
