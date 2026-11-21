# JDT LS Integration

## Pin

| Field | Value |
|-------|-------|
| Version | `1.57.0` |
| Archive | `jdt-language-server-1.57.0-202602261110.tar.gz` |
| SHA-256 | `f7ffa93fe1bbbea95dac13dd97cdcd25c582d6e56db67258da0dcceb2302601e` |
| Source | `https://download.eclipse.org/jdtls/milestones/1.57.0/` |

## Provision

```bash
bash .github/scripts/provision-jdtls.sh /tmp/desk-jdtls
export DESK_JAVA_LS=/tmp/desk-jdtls/bin/jdtls
```

Java API: `JdtLsProvisioner.ensureInstalled(root)`.

## CI

Job **Live JDT LS integration** (`jdtls-it`) sets `DESK_JDTLS_CI=1`, provisions the pin, runs `LiveJdtLsIntegrationTest`, and **fails if the test is skipped**.

## Local

```bash
export DESK_JDTLS_IT=1
export DESK_JAVA_LS=/path/to/jdtls
./mvnw -pl modules/desk-java -am test -Dtest=LiveJdtLsIntegrationTest
```

## Covered against real server

initialize/initialized, didOpen, completion, hover, documentSymbol, formatting, shutdown/exit.

## Limits

Rename/codeAction/multi-file apply against live jdtls are exercised in the TUI client with mocks; expand the live IT further as CI time allows. Spring Boot classpath resolve still needs network dependency download.
