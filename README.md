# luther

Luther is a Kotlin Multiplatform toolkit for building AI chat experiences. It ships as two
independently consumable artifacts:

| Artifact | Coordinates | What it is |
|---|---|---|
| **luther-core** | `com.strangeparticle:luther-core:<version>` | Pure-Kotlin engine: AI provider clients (Anthropic, OpenAI), a chat session/state machine, and a tool-call framework. No UI. |
| **luther-cmp** | `com.strangeparticle:luther-cmp:<version>` | Compose Multiplatform chat UI. Depends on — and re-exports (`api`) — luther-core. |

## Platform support

| Target | luther-core | luther-cmp |
|---|---|---|
| JVM (desktop: macOS / Linux / Windows) | ✅ | ✅ |
| Web (`wasmJs`) | ✅ | ✅ |
| Android | ✅ | ✅ |
| iOS (`iosArm64` / `iosSimulatorArm64`) | ✅ | ✅ |
| macOS native (`macosArm64` / `macosX64`) | ✅ | — |
| Linux native (`linuxX64`) | ✅ | — |
| Windows native (`mingwX64`) | ✅ | — |

## Versioning & toolchain

**Versioning.** The version is set in one place — `lutherVersion` in
[`gradle.properties`](gradle.properties); the runtime `LutherVersion.VERSION` constant and the
Maven coordinates are generated from it. To release, bump that line and tag `vX.Y.Z` (see
[docs/README_Versions.md](docs/README_Versions.md)).

**Pinned toolchain.** luther uses the exact Kotlin / Compose / Gradle / AGP set the Compose
Multiplatform template validates together — currently **Kotlin 2.4.0, Compose 1.11.1, Gradle
9.1.0, AGP 9.0.1** (see [`gradle/libs.versions.toml`](gradle/libs.versions.toml)) — so the full
native / iOS / web / Android matrix builds reliably. Because Kotlin consumption is forward-only,
consumers must be on **Kotlin ≥ luther's**.

## Local development — the composite-build dev loop

When you're developing luther and a consumer at the same time,
you don't publish on every change. Instead the consumer pulls luther in as a **Gradle
composite build**, so edits to luther source are picked up instantly with full
code-navigation in Android Studio — no publish step.

How a consumer wires it up:

1. Depend on luther normally, e.g. `implementation("com.strangeparticle:luther-cmp:<version>")`.
2. In the consumer's `settings.gradle.kts`, include luther's build behind a property:

   ```kotlin
   if (providers.gradleProperty("lutherDev").orNull == "true") {
       includeBuild("../luther")
   }
   ```

3. Turn it on locally by adding `lutherDev=true` to your machine-global
   `~/.gradle/gradle.properties` (or a project-local `gradle.properties`).

With `lutherDev=true`, Gradle substitutes the published `com.strangeparticle:luther-*`
coordinates with luther's local source: edit luther → rebuild the consumer → the change is
there, one incremental build graph, ⌘-click and refactor across both projects, breakpoints
in luther debuggable through the consumer. With it unset (CI, releases, other developers),
the consumer resolves the published artifact from Maven Central as usual.

Requirements: matching toolchain (the pinning above), the sibling checkout layout
(`luther` next to the consumer, hence `../luther`), and identical `group:artifact`
coordinates so the substitution fires.

## Building

### Prerequisites

- **JDK 17+** to bootstrap Gradle. The build provisions a JDK 17 toolchain automatically via
  the Foojay resolver.
- **Android targets:** an Android SDK with platform 36 + build-tools 36, located via
  `ANDROID_HOME` or `sdk.dir` in a local (git-ignored) `local.properties`.
- **Apple targets (iOS / macOS):** a macOS host with Xcode and its command-line tools.
- **Web:** Node is provisioned automatically by the Kotlin/JS toolchain.

### Build everything the current host supports

```bash
./gradlew assemble      # compile/link every target this OS can build
./gradlew build         # also run the tests this OS can run
```

### What each host can build

- **macOS (Apple Silicon or Intel)** — everything: JVM, web, Android, iOS, and the
  macOS / Linux / Windows native klibs (Linux and Windows are cross-compiled). This is the
  release host.
- **Linux x64** — JVM, web, Android, and `linuxX64`. Cannot build Apple (iOS / macOS) targets.
- **Windows** — JVM, web, Android, and `mingwX64` (use `gradlew.bat`). Cannot build Apple
  targets.

### Per-target tasks

```bash
# JVM (serves desktop macOS/Linux/Windows for luther-cmp via Compose Desktop)
./gradlew :luther-core:jvmJar :luther-cmp:jvmJar
./gradlew :luther-core:jvmTest

# Web
./gradlew :luther-core:wasmJsBrowserTest        # headless browser

# Android (library)
./gradlew :luther-core:assemble :luther-cmp:assemble

# iOS (runs in the simulator; macOS host only)
./gradlew :luther-core:iosSimulatorArm64Test

# macOS native (macOS host only)
./gradlew :luther-core:macosArm64Test            # or macosX64Test

# Linux native (Linux host only)
./gradlew :luther-core:linuxX64Test

# Windows native (Windows host only)
gradlew.bat :luther-core:mingwX64Test
```

Native and iOS tests only run on a matching host OS; CI runs them across macOS, Linux, and
Windows runners.

## Publishing

Both artifacts publish to Maven Central via the Central Portal, triggered by pushing a
`vX.Y.Z` tag — the [Release workflow](.github/workflows/release.yml) builds the full target
set on a macOS runner and publishes them as a single deployment. One-time credential and
namespace setup, plus the release procedure, are in [docs/RELEASING.md](docs/RELEASING.md).
You can preview the exact published layout locally with `./gradlew publishToMavenLocal`
(no credentials needed).

## Monitoring builds

Every push to `main` and every pull request runs [CI](.github/workflows/ci.yml) (build + tests
across macOS, Linux, and Windows); pushing a `vX.Y.Z` tag runs the
[Release workflow](.github/workflows/release.yml). Watch either from the browser or the terminal.

**Browser — the Actions tab:**

- All runs (live status): <https://github.com/strangeparticle/luther/actions>
- CI only: <https://github.com/strangeparticle/luther/actions/workflows/ci.yml>
- Release only: <https://github.com/strangeparticle/luther/actions/workflows/release.yml>

Click any run for per-job, per-step logs (streamed live while it runs).

**Terminal (`gh` CLI):**

```bash
gh run list                 # recent runs + status
gh run watch                # pick a run and live-stream it
gh run view <run-id> --web  # open a run in the browser
```

Add `-R strangeparticle/luther` to any of these if you run them outside a local clone.

## License

BSD 3-Clause. See [LICENSE](LICENSE).
