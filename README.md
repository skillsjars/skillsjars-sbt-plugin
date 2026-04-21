# SkillsJars sbt Plugin

`skillsjars-sbt-plugin` extracts SkillsJars published to Maven-compatible repositories into a local directory for downstream tooling.

It mirrors the behavior of the SkillsJars Gradle plugin:

- scans resolved dependencies for `com.skillsjars` artifacts
- extracts skill content from `META-INF/skills/` and `META-INF/resources/skills/`
- flattens each discovered skill root into `skillsjars__...`
- clears the destination directory before writing
- fails on extracted path collisions

## Usage

Add the plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("com.skillsjars" % "skillsjars-sbt-plugin" % "<version>")
```

Enable it in your build:

```scala
enablePlugins(com.skillsjars.sbt.SkillsJarsPlugin)

skillsJarsOutputDir := Some(file("target/skills"))
libraryDependencies += "com.skillsjars" % "example-skill" % "1.0.0"
```

Run the extraction task:

```text
sbt extractSkillsJars
```

You can also pass the output directory directly:

```text
sbt "extractSkillsJars target/skills"
```

## Settings

- `skillsJarsOutputDir`: default destination directory used when the task is invoked without an argument
- `skillsJarsConfigurations`: configurations to scan; empty means all resolved configurations
- `skillsJarsOrganization`: dependency organization to treat as SkillsJars, default `com.skillsjars`
