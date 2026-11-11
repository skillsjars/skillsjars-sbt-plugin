package com.skillsjars.sbt

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.complete.DefaultParsers.spaceDelimited
import sbtcompat.PluginCompat._

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

    @transient val extractSkillsJars: InputKey[File] =
      sbt.inputKey[File](
        "Extract the build's SkillsJars (every project's, at the highest revision) into a target directory. Pass a directory argument or set skillsJarsOutputDir."
      )

    @transient val skillsJarsFiles: TaskKey[Seq[File]] =
      sbt.taskKey[Seq[File]](
        "The build's SkillsJars (what extractSkillsJars extracts): every project's, one jar per module at its highest revision."
      )

    @transient val packageSkillsJars: TaskKey[Seq[File]] =
      sbt.taskKey[Seq[File]](
        "Package local skills into managed resources under META-INF/skills."
      )
  }

  import autoImport._

  /** One project's SkillsJars: its `skillsJarsConfigurations`' jars, transitive ones included. */
  private val skillsJarsProjectJars: TaskKey[Seq[SkillsJar]] =
    sbt.taskKey[Seq[SkillsJar]]("This project's SkillsJars, before combining them with the other projects'.")

  /** Every project's SkillsJars, in every build, one per module at its highest revision; logs each revision conflict. */
  private val buildSkillsJars: Def.Initialize[Task[ResolvedSkillsJars]] = Def.task {
    val resolved = SkillsJarsResolver.resolve(skillsJarsProjectJars.all(ScopeFilter(inAnyProject)).value.flatten)
    val log = streams.value.log
    resolved.conflicts.foreach(conflict => log.warn(conflict.message))
    resolved
  }

  override def requires: Plugins = JvmPlugin
  override def trigger = allRequirements

  override lazy val projectSettings: Seq[Def.Setting[?]] = Seq(
    ivyConfigurations += Skills,
    skillsJarsOutputDir := None,
    skillsJarsConfigurations := Seq(Skills),
    skillsJarsSourceDir := new File(baseDirectory.value, "skills"),
    skillsJarsAllowedTools := Map.empty,
    extractSkillsJars / aggregate := false,
    skillsJarsFiles / aggregate := false,
    clean := clean
      .dependsOn(Def.task {
        skillsJarsOutputDir.value.toSeq.foreach { path =>
          val resolved = if (path.isAbsolute) path else new File(baseDirectory.value, path.getPath)
          IO.delete(resolved)
        }
      })
      .value,
    skillsJarsProjectJars := Def.uncached(
      SkillsJarsExtractor.findSkillsJars(updateFull.value, skillsJarsConfigurations.value.map(_.name).toSet)
    ),
    extractSkillsJars := {
      val outputArg = spaceDelimited("<dir>").parsed.headOption
      val outputDir = resolveOutputDir(outputArg, skillsJarsOutputDir.value, baseDirectory.value)

      SkillsJarsExtractor.extract(
        skillsJars = buildSkillsJars.value.jars,
        outputPath = outputDir.toPath,
        log = streams.value.log
      )

      outputDir
    },
    skillsJarsFiles := Def.uncached(buildSkillsJars.value.jars.map(_.path.toFile)),
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
