package com.skillsjars.sbt

import sbt.internal.util.ManagedLogger
import sbt.librarymanagement.ConfigurationReport
import sbt.librarymanagement.ModuleReport
import sbt.librarymanagement.UpdateReport

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.jar.JarFile
import scala.collection.mutable

private[sbt] object SkillsJarsExtractor {
  val DefaultOrganization = "com.skillsjars"
  val SkillsPrefixes = List("META-INF/skills/", "META-INF/resources/skills/")

  final case class SkillsJarCoordinate(id: String, path: Path)

  def extract(
      report: UpdateReport,
      outputPath: Path,
      organization: String,
      configurationNames: Set[String],
      log: ManagedLogger
  ): Int = {
    require(outputPath != null, "outputPath must not be null")

    val skillsJars = findSkillsJars(report, organization, configurationNames)
    log.info(s"Extracting SkillsJars to: $outputPath")

    deleteDirectory(outputPath, log)
    Files.createDirectories(outputPath)

    log.info(s"Found ${skillsJars.size} SkillsJar(s)")

    val extractedPaths = mutable.Map.empty[String, String]
    skillsJars.foreach { jar =>
      extractSkillsJar(jar, outputPath, extractedPaths, log)
    }

    log.info("Successfully extracted SkillsJars")
    skillsJars.size
  }

  def findSkillsJars(
      report: UpdateReport,
      organization: String,
      configurationNames: Set[String]
  ): Vector[SkillsJarCoordinate] = {
    val configurations =
      if (configurationNames.nonEmpty) {
        report.configurations.filter(config => configurationNames.contains(config.configuration.name))
      } else {
        report.configurations
      }

    val discovered = mutable.LinkedHashMap.empty[String, Path]

    configurations.foreach { config =>
      config.modules.foreach { module =>
        if (module.module.organization == organization) {
          jarFile(module).foreach { file =>
            val id = s"${module.module.organization}:${module.module.name}:${module.module.revision}"
            discovered.getOrElseUpdate(id, file.toPath)
          }
        }
      }
    }

    discovered.iterator.map { case (id, path) => SkillsJarCoordinate(id, path) }.toVector
  }

  private def jarFile(module: ModuleReport): Option[java.io.File] =
    module.artifacts.collectFirst {
      case (artifact, file) if artifact.`type` == "jar" || file.getName.endsWith(".jar") => file
    }

  private def extractSkillsJar(
      jar: SkillsJarCoordinate,
      outputPath: Path,
      extractedPaths: mutable.Map[String, String],
      log: ManagedLogger
  ): Unit = {
    if (!Files.exists(jar.path)) {
      log.warn(s"Artifact file not found: ${jar.id}")
      return
    }

    log.info(s"Extracting: ${jar.id}")

    val skillRoots = mutable.LinkedHashMap.empty[String, String]
    withJar(jar.path) { jarFile =>
      val entries = jarFile.entries()
      while (entries.hasMoreElements) {
        val entry = entries.nextElement()
        val relativePath = stripSkillsPrefix(entry.getName)
        if (relativePath.isDefined && entry.getName.endsWith("/SKILL.md")) {
          val skillRoot = relativePath.get.stripSuffix("/SKILL.md")
          val flattenedRoot = skillRoot.replace("/", "__")
          skillRoots.put(s"$skillRoot/", flattenedRoot)
        }
      }
    }

    withJar(jar.path) { jarFile =>
      val entries = jarFile.entries()
      while (entries.hasMoreElements) {
        val entry = entries.nextElement()
        if (!entry.isDirectory) {
          stripSkillsPrefix(entry.getName).foreach { relativePath =>
            val maybeRoot = skillRoots.collectFirst {
              case (skillRoot, flattenedRoot) if relativePath.startsWith(skillRoot) =>
                (skillRoot, flattenedRoot)
            }

            maybeRoot match {
              case Some((skillRoot, flattenedRoot)) =>
                val remainder = relativePath.substring(skillRoot.length)
                val conflictKey = s"skillsjars__${flattenedRoot}/$remainder"
                extractedPaths.get(conflictKey).foreach { existing =>
                  sys.error(
                    s"Path conflict detected: $conflictKey exists in both $existing and ${jar.id}"
                  )
                }

                val targetPath = outputPath.resolve(s"skillsjars__${flattenedRoot}").resolve(remainder)
                Files.createDirectories(targetPath.getParent)
                jarFile.getInputStream(entry).use { input =>
                  Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
                extractedPaths.put(conflictKey, jar.id)
                log.debug(s"Extracted: $conflictKey")

              case None =>
                log.warn(s"Skipping file not under a SKILL.md root: $relativePath")
            }
          }
        }
      }
    }
  }

  private def stripSkillsPrefix(entryName: String): Option[String] =
    SkillsPrefixes.collectFirst {
      case prefix if entryName.startsWith(prefix) => entryName.substring(prefix.length)
    }

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

  private def withJar[A](path: Path)(f: JarFile => A): A = {
    val jar = new JarFile(path.toFile)
    try f(jar)
    finally jar.close()
  }

  implicit private final class AutoCloseableOps[A <: AutoCloseable](private val resource: A)
      extends AnyVal {
    def use[B](f: A => B): B =
      try f(resource)
      finally resource.close()
  }
}
