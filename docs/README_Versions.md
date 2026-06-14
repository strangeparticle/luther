# luther Versioning

How luther is versioned, where the version lives, and how to bump it. Point an AI agent at
this file when preparing a release.

## Scheme

luther uses [Semantic Versioning](https://semver.org): `MAJOR.MINOR.PATCH`.

- **Pre-1.0 is allowed.** While the API is still settling, versions stay in the `0.y.z`
  range (e.g. `0.1.0`), which makes no stability promises.
- **Optional identifiers are allowed.** A pre-release suffix (`-rc.1`, `-beta.2`) and/or
  build metadata (`+abc1234`) may follow the triplet, e.g. `1.0.0-rc.1`.

## The one place the version lives

The version is declared in **exactly one** spot:

```
# gradle.properties
lutherVersion=0.1.0
```

Nothing else holds the literal version string. Everything below is derived from this line.

## How to bump the version

1. Edit the single line in `gradle.properties` (`lutherVersion=...`).
2. Build. The runtime constant, the Gradle project version, and the Maven coordinates all
   pick up the new value automatically.

Do **not** hand-edit any `.kt` file to change the version; the runtime constant is
generated.

## How it reaches the runtime

A Gradle task, **`GenerateKotlinVersionFile`** (in `luther-core/build.gradle.kts`), reads
`lutherVersion` and writes:

```
luther-core/build/generated/lutherVersion/com/strangeparticle/luther/core/LutherVersion.kt
  → object LutherVersion { const val VERSION = "0.1.0" }
```

That directory is a `commonMain` source directory of `luther-core`, wired so the task runs
before any compilation that consumes it. The generated file lives under `build/` and is
**git-ignored**, so the version is never duplicated in tracked source and cannot drift from
`gradle.properties`. Runtime code (on any platform) reads `LutherVersion.VERSION`.

## How it reaches build time and publishing

The root `build.gradle.kts` sets every module's Gradle `version` from `lutherVersion`, so
build/packaging logic and the Maven Central coordinates
(`com.strangeparticle:luther-core:<version>`, `…:luther-cmp:<version>`) all read the same
value.

## How a release is cut

Bump the line, commit, and push a `vX.Y.Z` tag. The release GitHub Actions workflow builds
and publishes both artifacts to Maven Central from that tag. (Release pipeline lands in a
later phase; see the project plan.)
