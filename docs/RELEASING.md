# Releasing luther

luther publishes two artifacts — `com.strangeparticle:luther-core` and
`com.strangeparticle:luther-cmp` — to **Maven Central** via the
[Central Portal](https://central.sonatype.com). Pushing a `vX.Y.Z` tag triggers the
[Release workflow](../.github/workflows/release.yml), which builds the full target set on a free
hosted macOS runner, signs in-memory, and publishes both artifacts as one deployment.

## Release procedure (summary)

1. Bump `lutherVersion` in [`gradle.properties`](../gradle.properties).
2. Commit and push to `main`.
3. Tag the tip of `main` and push the tag:
   ```bash
   git tag v0.1.0 && git push origin v0.1.0
   ```
4. The tag push runs the Release workflow → both artifacts published to Maven Central.
5. Verify they resolve from Central.

## Step detail

**1. Version.** `lutherVersion` is the single source of truth (see
[README_Versions.md](README_Versions.md)); it drives both the generated `LutherVersion.VERSION`
and the Gradle project version.

**2. Land on `main`.** Tag the commit that carries the release-ready state — the tip of `main`
after the version bump (a merge/squash commit if the change came via a branch, or a direct commit).

**3. Tag + push.** `git tag` is local and triggers nothing; the **tag push** is the trigger
(`release.yml` runs `on: push: tags: ['v*']`). The workflow runs
`./gradlew publishAndReleaseToMavenCentral` (auto-release, single deployment).

**4/5. Publish + verify.** After the run is green, confirm `luther-core-<version>.pom` and the
native klibs are fetchable from `repo1.maven.org`, or resolve the coordinates in a scratch Gradle
build. (The Central search index lags the actual publish by a while.)

### Local verification without credentials
`publishToMavenLocal` needs no credentials and no signing (signing is skipped unless
`signingInMemoryKey` is present), so you can inspect the exact artifact layout:
```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/com/strangeparticle
```
This is **not** a release — it writes to `~/.m2` only. For iterating luther against a consumer,
use the composite build (`includeBuild("../luther")`, gated by the `lutherDev` property), not a
Central release.

### Local release to Central (break-glass — for when CI is down)
Follow the same procedure (bump → commit → tag → push), then run the *upload* from the tagged
commit on a macOS host, supplying the credentials as env vars (values from
`~/Documents/Strange Particle/maven-central/`):
```bash
export ORG_GRADLE_PROJECT_mavenCentralUsername=<token username>
export ORG_GRADLE_PROJECT_mavenCentralPassword=<token password>
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys 12F18C7A5AB0343E)"
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```
Prefer a git-ignored `.env` you `source` over persisting these in `.zshrc`. Maven Central versions
are immutable, so a duplicate attempt fails harmlessly.

## GitHub Actions secrets (this repo)

Set on `strangeparticle/luther` (**Settings → Secrets and variables → Actions**). The workflow maps
each to an `ORG_GRADLE_PROJECT_*` env var:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user-token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user-token password |
| `GPG_SIGNING_KEY` | ASCII-armored secret key: `gpg --armor --export-secret-keys 12F18C7A5AB0343E` |

There is **no** signing-passphrase secret — the org signing key has no passphrase (see rationale).

## One-time setup (org-level — not luther-specific)

The Maven Central account, the `com.strangeparticle` namespace + DNS verification, the publishing
token, and the **org-wide GPG signing key** are shared across all strangeparticle projects and are
documented in **`~/Documents/Strange Particle/maven-central/maven-central-setup.md`** (gpg install:
`~/Downloads/_installed/330_gnupg/`). Reuse that identity — do not mint a per-project key.

## Decisions & rationale

- **In-memory signing (Bouncy Castle).** The publish plugin signs from an in-memory armored key on
  the build host; `gpg` is only needed once to mint/export the key, never at release time. Works
  identically locally and in CI.
- **No signing passphrase.** A passphrase only helps when stored apart from the key; an automated
  publishing key must hand it to the build, co-locating them, which negates the benefit. Real
  controls instead: a dedicated key, a 2-year expiry, and a stashed revocation certificate.
- **Org-wide signing key.** One identity signs all `com.strangeparticle` artifacts; it lives at the
  org level, not in this repo.
- **Tag-triggered CI; local is break-glass.** Releases are rare, so CI's clean-room/free macOS
  build is the default; the fast dev loop is the composite build, not a Central release.
