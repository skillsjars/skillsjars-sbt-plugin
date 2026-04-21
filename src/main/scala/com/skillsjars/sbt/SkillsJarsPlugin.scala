package com.skillsjars.sbt

import sbt.Keys.baseDirectory
import sbt.Keys.ivyConfigurations
import sbt.Keys.streams
import sbt.Keys.updateFull
import sbt.Configuration
import sbt.Def
import sbt.File
import sbt.InputKey
import sbt.Plugins
import sbt.SettingKey
import sbt.AutoPlugin
import sbt.config
import sbt.file
import sbt.plugins.JvmPlugin
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

    val extractSkillsJars: InputKey[File] =
      sbt.inputKey[File](
        "Extract SkillsJars into a target directory. Pass a directory argument or set skillsJarsOutputDir."
      )
  }

  import autoImport._

  override def requires: Plugins = JvmPlugin
  override def trigger = allRequirements

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    ivyConfigurations += Skills,
    skillsJarsOutputDir := None,
    skillsJarsConfigurations := Seq(Skills),
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
    }
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
