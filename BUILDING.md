# Building

One source tree, two jars. Which Minecraft version a jar targets is a Maven
profile, not a branch.

| Profile | paper-api | `api-version` | Output |
|---|---|---|---|
| `mc1211` (default) | 1.21.11 | `1.21` | `target/market-<version>+mc1.21.11.jar` |
| `mc262` | 26.2 | `26.2` | `target/market-<version>+mc26.2.jar` |

Both compile with `--release 21`, so either jar loads on either server. The
difference is the API they are checked against and the `api-version` they declare.

## Requirements

- **Java 21** for the 1.21.11 build.
- **Java 25** for the 26.2 build. The 26.2 paper-api is compiled to class file
  version 69, which a Java 21 compiler cannot read; the error is
  `bad class file … class file has wrong version 69.0`. Point `JAVA_HOME` at a
  JDK 25 for that profile.
- Maven 3.9+.
- `libs/MultiBank-1.0.0.jar` and `libs/Vault-1.7.3.jar` are in the repository and
  resolved from there (system scope, compile-time only).

## Commands

```sh
# 1.21.11 (default profile)
mvn -B clean package

# 26.2
JAVA_HOME=/path/to/jdk-25 mvn -B clean package -P mc262

# both, in one go
mvn -B clean package
JAVA_HOME=/path/to/jdk-25 mvn -B package -P mc262
```

The second command deliberately omits `clean`, so both jars end up in `target/`
together.

## Checking a build

```sh
unzip -p target/market-*+mc26.2.jar plugin.yml | grep api-version   # -> 26.2
javap -verbose -cp target/market-*.jar pt.henrique.market.Market | grep major
```

`major` should read `65` (Java 21) for both jars.
