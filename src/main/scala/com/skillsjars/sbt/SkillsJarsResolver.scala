package com.skillsjars.sbt

import java.nio.file.Path

/** One SkillsJar a project resolved: its module and its jar. */
private[sbt] final case class SkillsJar(organization: String, name: String, revision: String, path: Path) {
  def id: String = s"$organization:$name:$revision"
}

/** A module that projects resolved at different revisions, and the one used. */
private[sbt] final case class SkillsJarConflict(organization: String, name: String, revisions: Seq[String], chosen: String) {
  def message: String =
    s"SkillsJars: $organization:$name is resolved at ${revisions.mkString(", ")} across the build's projects; using $chosen"
}

/** The build's SkillsJars: one per module, at the highest revision any project resolved. */
private[sbt] final case class ResolvedSkillsJars(jars: Vector[SkillsJar], conflicts: Vector[SkillsJarConflict])

private[sbt] object SkillsJarsResolver {

  /** Every project's SkillsJars, one per module (in first-seen order), at its highest revision. */
  def resolve(found: Seq[SkillsJar]): ResolvedSkillsJars = {
    val modules = found.map(jar => (jar.organization, jar.name)).distinct
    val byModule = found.groupBy(jar => (jar.organization, jar.name))
    val chosen = modules.map { module =>
      val candidates = byModule(module)
      val highest = candidates.reduceLeft((a, b) => if (compareVersions(b.revision, a.revision) > 0) b else a)
      val revisions = candidates.map(_.revision).distinct.sortWith(compareVersions(_, _) < 0)
      val conflict =
        if (revisions.size > 1) Some(SkillsJarConflict(module._1, module._2, revisions, highest.revision))
        else None
      (highest, conflict)
    }
    ResolvedSkillsJars(chosen.map(_._1).toVector, chosen.flatMap(_._2).toVector)
  }

  private sealed trait Token
  private final case class Num(value: BigInt) extends Token
  private final case class Qualifier(value: String) extends Token

  private def tokens(version: String): List[Token] =
    """\d+|[a-zA-Z]+""".r.findAllIn(version).map { t =>
      if (t.head.isDigit) Num(BigInt(t)) else Qualifier(t.toLowerCase)
    }.toList

  /**
   * Orders versions like Maven and Coursier for the common cases: numeric parts compare
   * as numbers (`1.10` > `1.9`), a longer release is newer (`1.0.1` > `1.0`), and a
   * qualifier after a release is a pre-release of it (`1.0-RC1` < `1.0`).
   */
  def compareVersions(a: String, b: String): Int = {
    def loop(as: List[Token], bs: List[Token]): Int = (as, bs) match {
      case (Nil, Nil)                     => 0
      case (Nil, (_: Num) :: _)           => -1
      case (Nil, (_: Qualifier) :: _)     => 1
      case ((_: Num) :: _, Nil)           => 1
      case ((_: Qualifier) :: _, Nil)     => -1
      case (Num(x) :: at, Num(y) :: bt)   => if (x == y) loop(at, bt) else x.compare(y)
      case (Num(_) :: _, Qualifier(_) :: _) => 1
      case (Qualifier(_) :: _, Num(_) :: _) => -1
      case (Qualifier(x) :: at, Qualifier(y) :: bt) =>
        val c = x.compareTo(y)
        if (c == 0) loop(at, bt) else c
    }
    loop(tokens(a), tokens(b))
  }
}
