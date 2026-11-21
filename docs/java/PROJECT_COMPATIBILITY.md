# Java Project Compatibility Guide

Desk detects and works with common real-world Java layouts under `testdata/fixtures/` and user workspaces.

## Supported shapes

| Category | Detection | Build | Notes |
|----------|-----------|-------|-------|
| Plain Java | `src/**/*.java` | `javac` via BuildService | No build file required |
| Maven app | `pom.xml` | `./mvnw` or `mvn` | Wrapper preferred |
| Maven library | `pom.xml` packaging jar | same | Java target may differ from Desk's 25 |
| Gradle Groovy | `build.gradle` + `settings.gradle` | `./gradlew` or `gradle` | |
| Gradle Kotlin DSL | `*.gradle.kts` | same | |
| Multi-module Maven | aggregator `pom.xml` | reactor build | |
| Multi-module Gradle | `settings.gradle` includes | | |
| Spring Boot | Maven/Gradle + Boot deps | | Full resolve needs network |
| External deps | Maven/Gradle | | Classpath via toolchains / LS |
| Different Java targets | POM/`toolchain` | | Workspace JDK pin (`.desk/jdk.json`) |

## After open

1. `ProjectDetector.detect(root)` chooses build system.
2. Workspace JDK from `.desk/jdk.json` or discovery — never overwrites system `JAVA_HOME`.
3. Builds use wrappers when present.
4. Dependency / source-root changes: reload workspace (`desk workspace open` / reopen) so JDT LS and classpath refresh.

## Fixtures

See `testdata/fixtures/README.md`. Detection is covered by `ProjectFixtureTest`.
