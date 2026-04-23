package com.skillsjars.sbt

import sbt.ScmInfo
import sbt.internal.util.ManagedLogger

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.regex.Pattern
import scala.collection.JavaConverters._
import scala.collection.mutable

private[sbt] object SkillsJarsPackager {
  private val PropertyPrefix = "skillsjars.skill."
  private val GitHubUrlPattern =
    Pattern.compile(""".*github\.com[:/]([^/]+)/([^/]+?)(?:\.git)?/?$""")

  def packageSkills(
      skillsDir: Path,
      outputRoot: Path,
      projectOrganization: String,
      scm: Option[ScmInfo],
      allowedToolsBySkill: Map[String, String],
      log: ManagedLogger
  ): Seq[java.io.File] = {
    require(skillsDir != null, "skillsDir must not be null")
    require(outputRoot != null, "outputRoot must not be null")

    val packageRoot = resolvePackageRoot(projectOrganization, scm)
    val targetRoot = outputRoot.resolve(packageRoot)

    deleteDirectory(targetRoot, log)

    if (!Files.isDirectory(skillsDir)) {
      log.debug(s"Skills directory not found: $skillsDir")
      return Seq.empty
    }

    log.info(s"Packaging skills from: $skillsDir")

    val skillDirs = listDirectories(skillsDir)
    if (skillDirs.isEmpty) {
      log.warn(s"No skill directories found in: $skillsDir")
      return Seq.empty
    }

    Files.createDirectories(targetRoot)

    val packagedFiles = mutable.ArrayBuffer.empty[java.io.File]
    skillDirs.foreach { skillDir =>
      val skillMarker = skillDir.resolve("SKILL.md")
      if (!Files.exists(skillMarker)) {
        log.warn(s"Skipping directory without SKILL.md: ${skillDir.getFileName}")
      } else {
        validateAllowedTools(skillMarker, allowedToolsBySkill)
        packagedFiles ++= copySkill(
          skillDir = skillDir,
          outputRoot = targetRoot.resolve(skillDir.getFileName.toString),
          log = log
        )
      }
    }

    log.info(s"Skills packaged to: $targetRoot")
    packagedFiles.toVector
  }

  private def resolvePackageRoot(projectOrganization: String, scm: Option[ScmInfo]): Path =
    parseGitHubCoordinates(scm).map {
      case (org, repo) => Path.of("META-INF", "skills", org, repo)
    }.getOrElse {
      val groupPath = projectOrganization.replace('.', '/')
      Path.of("META-INF", "skills").resolve(groupPath)
    }

  private def parseGitHubCoordinates(scm: Option[ScmInfo]): Option[(String, String)] =
    scm.toSeq.flatMap { info =>
      info.browseUrl.toExternalForm +: info.devConnection.toSeq
    }.flatMap(url => parseGitHubCoordinates(url))
      .headOption

  private def parseGitHubCoordinates(url: String): Option[(String, String)] = {
    val matcher = GitHubUrlPattern.matcher(url)
    if (matcher.matches()) Some((matcher.group(1), matcher.group(2))) else None
  }

  private def listDirectories(path: Path): Vector[Path] =
    withCloseable(Files.list(path)) { stream =>
      stream.iterator().asScala.filter(Files.isDirectory(_)).toVector.sortBy(_.getFileName.toString)
    }

  private def copySkill(
      skillDir: Path,
      outputRoot: Path,
      log: ManagedLogger
  ): Vector[java.io.File] =
    withCloseable(Files.walk(skillDir)) { stream =>
      stream.iterator().asScala.filter(Files.isRegularFile(_)).toVector.map { file =>
        val relativePath = skillDir.relativize(file)
        val targetFile = outputRoot.resolve(relativePath.toString)
        Files.createDirectories(targetFile.getParent)
        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
        log.debug(s"Copied: ${targetFile.toString}")
        targetFile.toFile
      }
    }

  private def validateAllowedTools(
      skillMdFile: Path,
      allowedToolsBySkill: Map[String, String]
  ): Unit = {
    val content = new String(Files.readAllBytes(skillMdFile), StandardCharsets.UTF_8)
    val maybeFrontmatter = extractFrontmatter(content)
    maybeFrontmatter.foreach { frontmatter =>
      val maybeSkillName = extractFrontmatterValue(frontmatter, "name")
      val maybeAllowedTools = extractFrontmatterValue(frontmatter, "allowed-tools")

      (maybeSkillName, maybeAllowedTools) match {
        case (Some(skillName), Some(allowedTools)) =>
          val propertyName = allowedToolsPropertyName(skillName)
          allowedToolsBySkill.get(skillName) match {
            case None =>
              sys.error(
                s"SKILL.md for '$skillName' has allowed-tools but build is missing '$propertyName'. " +
                  s"""Add ${propertyName} := "$allowedTools" to skillsJarsAllowedTools."""
              )
            case Some(value) if value != allowedTools =>
              sys.error(
                s"'$propertyName' value '$value' does not match SKILL.md allowed-tools '$allowedTools'"
              )
            case _ =>
          }
        case _ =>
      }
    }
  }

  private def extractFrontmatter(content: String): Option[String] = {
    val lines = content.split("\n", -1)
    if (lines.isEmpty || lines(0).trim != "---") None
    else {
      val builder = new StringBuilder
      var index = 1
      while (index < lines.length && lines(index).trim != "---") {
        builder.append(lines(index)).append('\n')
        index += 1
      }
      if (index < lines.length) Some(builder.toString) else None
    }
  }

  private def extractFrontmatterValue(frontmatter: String, key: String): Option[String] = {
    val pattern = Pattern.compile("^" + Pattern.quote(key) + ":\\s*(.+)$", Pattern.MULTILINE)
    val matcher = pattern.matcher(frontmatter)
    if (matcher.find()) Some(matcher.group(1).trim) else None
  }

  private def allowedToolsPropertyName(skillName: String): String =
    s"$PropertyPrefix$skillName.allowed-tools"

  private def deleteDirectory(path: Path, log: ManagedLogger): Unit = {
    if (Files.exists(path)) {
      Files
        .walk(path)
        .sorted(Comparator.reverseOrder())
        .forEach { p =>
          try Files.deleteIfExists(p)
          catch {
            case _: IOException =>
              log.warn(s"Failed to delete: $p")
          }
        }
    }
  }

  private def withCloseable[A <: AutoCloseable, B](resource: A)(f: A => B): B =
    try f(resource)
    finally resource.close()
}
