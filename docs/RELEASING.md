# Releasing luther

luther publishes `luther-core` and `luther-cmp` to **Maven Central** through the
[Central Portal](https://central.sonatype.com). A push of a `vX.Y.Z` tag triggers the
[Release workflow](../.github/workflows/release.yml), which builds the full target set on a
macOS runner and publishes both artifacts as one deployment.

## One-time setup (manual — done by the maintainer)

These produce the four GitHub Actions secrets the release workflow needs. Nothing here is
committed to the repo.

### 1. Central Portal account + namespace

1. Sign in at <https://central.sonatype.com>.
2. Register the namespace **`com.strangeparticle`**. Central gives you a verification
   **DNS TXT record** — add it to the `strangeparticle.com` DNS zone (at the provider that
   hosts the domain). This proves ownership; it does **not** require the domain to be linked
   to GitHub. Verification usually completes within minutes of the record propagating.
3. Under **Account → Generate User Token**, create a token. You get a **username** and
   **password** pair — these are the publishing credentials (not your portal login).

### 2. GPG signing key

Central requires every artifact to be signed.

```bash
# Generate a key (RSA 4096, tied to admin@strangeparticle.com)
gpg --full-generate-key

# Find the key id (the long hex after "sec   rsa4096/")
gpg --list-secret-keys --keyid-format LONG

# Publish the PUBLIC key so Central can verify signatures
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --keyserver keys.openpgp.org      --send-keys <KEY_ID>

# Export the ASCII-armored SECRET key (the whole block, including the BEGIN/END lines)
gpg --armor --export-secret-keys <KEY_ID>
```

### 3. GitHub repository secrets

In the repo: **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user-token **username** |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user-token **password** |
| `SIGNING_KEY` | the full ASCII-armored secret key from step 2 |
| `SIGNING_PASSWORD` | the passphrase for that key |

## Cutting a release

1. Bump the single version line in [`gradle.properties`](../gradle.properties)
   (`lutherVersion=…`). See [README_Versions.md](README_Versions.md).
2. Commit and push to `main`.
3. Tag and push the tag:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

4. The Release workflow runs `./gradlew publishAndReleaseToMavenCentral`, which uploads and
   auto-releases both artifacts. They appear on Maven Central a short while later.

## Verifying locally without credentials

`publishToMavenLocal` works with no Central credentials and no signing key (signing is
skipped unless `signingInMemoryKey` is present), so you can inspect the exact artifact
layout that will be published:

```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/com/strangeparticle
```

## Publishing from a workstation (optional, instead of CI)

Set the same values as environment variables and run the release task directly:

```bash
export ORG_GRADLE_PROJECT_mavenCentralUsername=...
export ORG_GRADLE_PROJECT_mavenCentralPassword=...
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys <KEY_ID>)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=...
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

A macOS host is required to build the Apple targets and cross-compile the Linux/Windows
klibs in one pass.
