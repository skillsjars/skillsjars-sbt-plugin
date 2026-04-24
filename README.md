# SkillsJars sbt Plugin

`skillsjars-sbt-plugin` extracts SkillsJars published to Maven-compatible repositories into a local directory for downstream tooling, and can package local skills into your project's jar resources.

It mirrors the behavior of the SkillsJars Gradle plugin:

- extracts all dependencies declared in the `Skills` scope (any group ID)
- looks for skill content in `META-INF/skills/` and `META-INF/resources/skills/`
- flattens each discovered skill root into `skillsjars__...`
- clears the destination directory before writing
- fails on extracted path collisions
- packages local `skills/` directories into `META-INF/skills/...`
- validates `allowed-tools` frontmatter declarations during packaging

## Usage

Add the plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("com.skillsjars" % "skillsjars-sbt-plugin" % "<version>")
```

Configure it in your build:

```scala
skillsJarsOutputDir := Some(file("target/skills"))
libraryDependencies += "com.skillsjars" % "example-skill" % "1.0.0" % Skills
```

Declaring dependencies in the `Skills` scope keeps SkillsJars off your compile and runtime classpath — they are resolved only for extraction.

Run the extraction task:

```text
sbt extractSkillsJars
```

You can also pass the output directory directly:

```text
sbt "extractSkillsJars target/skills"
```

If you want a SkillsJar to be on the classpath **and** extracted, declare it twice — once normally and once with `% Skills`:

```scala
libraryDependencies ++= Seq(
  "com.skillsjars" % "example-skill" % "1.0.0",
  "com.skillsjars" % "example-skill" % "1.0.0" % Skills
)
```

To package local skills into your jar, put them under `skills/<skill-name>/...` with a `SKILL.md` marker file:

```text
skills/
  my-skill/
    SKILL.md
    prompt.txt
```

The plugin automatically adds packaged skills to `Compile / resourceGenerators`, so `compile` and `packageBin` will include them. You can also run the task directly:

```text
sbt packageSkillsJars
```

By default the plugin uses `META-INF/skills/<github-org>/<github-repo>/<skill-name>/...` when `scmInfo` points at GitHub. Otherwise it falls back to `META-INF/skills/<organization-path>/<skill-name>/...`.

If a `SKILL.md` frontmatter includes `allowed-tools`, declare the matching value in `skillsJarsAllowedTools`:

```scala
skillsJarsAllowedTools := Map(
  "my-skill" -> "Bash Read Edit"
)
```

## Settings

- `skillsJarsOutputDir`: default destination directory used when the task is invoked without an argument
- `skillsJarsConfigurations`: configurations to scan for SkillsJars dependencies; defaults to `Seq(Skills)`
- `skillsJarsSourceDir`: local directory containing skills to package; defaults to `baseDirectory.value / "skills"`
- `skillsJarsAllowedTools`: expected `allowed-tools` values keyed by skill name for packaging validation
