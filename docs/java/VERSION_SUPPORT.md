# Desk Java Version Support Matrix

| Version | Role | LTS? |
|---------|------|------|
| **25** | Primary development & CI baseline | **Yes** |
| **26** | Compatibility target | No |
| **27** | Latest feature-release compatibility target | No |
| **29** | Future LTS migration target | Expected |
| 21 | Supported as project JDK | Yes |
| 17 / 11 / 8 | Supported as project JDKs | Yes |

Desk itself builds and ships against **Java 25 LTS**. Non-LTS versions (26, 27) are never advertised as LTS.

Project workspaces may pin any discovered JDK (for example Java 21) without changing the Desk runtime JVM or other projects' pins.

See also: `desk java policy`, `desk java doctor`.
