# Desk real-world fixtures

Used by Phase 4 integration tests. Projects are intentionally minimal.

| Fixture | Kind |
|---------|------|
| plain-java | Plain sources |
| maven-app | Maven application (Java 25) |
| maven-lib | Maven library (Java 21 target) |
| gradle-groovy | Gradle Groovy DSL |
| gradle-kotlin | Gradle Kotlin DSL |
| spring-boot-minimal | Spring Boot POM shape |
| multi-maven | Multi-module Maven |
| multi-gradle | Multi-module Gradle (app + lib) |

Spring Boot dependency resolution requires network Maven repos — detection tests do not require a full resolve.
