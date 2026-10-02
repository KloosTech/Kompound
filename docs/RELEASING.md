# Releasing Kompound

Everything is automated in GitHub Actions (`.github/workflows/release.yml`). Jobs **skip, not fail**, when their secrets are missing, so the workflow is safe to run before you have configured anything.

## One-time setup (manual, outside the repo)

1. **Maven Central namespace**: central.sonatype.com → Namespaces → add `tech.kloos`, add the DNS TXT record the portal shows to `kloos.tech`, wait for "Verified".
2. **Central user token**: Account → Generate User Token → username/password pair.
3. **GPG key**: `gpg --full-generate-key` (RSA 4096), `gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`, export the private key: `gpg --export-secret-keys --armor <KEYID>`.
4. **GitHub repo secrets** (Settings → Secrets and variables → Actions). Create an `release` environment for the release job (optionally with a required reviewer) and put the secrets there:

   | Secret | Value |
   |--------|-------|
   | `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` | Central user token |
   | `SIGNING_IN_MEMORY_KEY` | armored private key (whole text) |
   | `SIGNING_IN_MEMORY_KEY_ID` | last 8 hex chars of the key id |
   | `SIGNING_IN_MEMORY_KEY_PASSWORD` | key passphrase |
   | `ANDROID_KEYSTORE_BASE64` | `base64 -i release.keystore` (optional; without it the APK is debug-signed and named `-unsigned`) |
   | `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | keystore details |

5. **GitHub Pages**: Settings → Pages → Source: *GitHub Actions* (publishes the web catalog on every push to `main`).
6. **Later, with an Apple Developer account**: iOS signing, TestFlight, and macOS notarisation (not wired yet; the iOS catalog currently builds for the simulator only).

## Two independent release tracks

The library and the catalog apps are released separately, so the library can ship often and the catalog only on milestones.

| Track | Tag | Workflow | Produces |
|-------|-----|----------|----------|
| **Library** | `vX.Y.Z` / `vX.Y.Z-alpha01` | `release.yml` | Signed upload of all libraries and Gradle plugins to Maven Central; a GitHub Release with generated notes (no binaries) |
| **Catalog apps** | `catalog-vX.Y.Z` | `catalog-release.yml` | Android APK and desktop installers (dmg, msi, deb) attached to a GitHub Release |
| **Web catalog** | none (push to `main`) | `catalog-web.yml` | GitHub Pages site, always the latest `main` |

Versions are independent: library `v0.4.0` can ship next to catalog `catalog-v1.0.0`. The catalog always builds against the library sources in the same commit.

## Releasing the library

1. Update `CHANGELOG.md`; make sure `main` is green.
2. Pre-releases (`-alphaNN`, `-betaNN`, `-rcNN`) are a good first step: `git tag -a v0.2.0-alpha01 -m "..." && git push origin v0.2.0-alpha01`. The job uploads to Central **without releasing**; inspect the deployment in the portal (central.sonatype.com, Publish, Deployments) and click *Publish* or *Drop*. Central versions are immutable once released.
3. Stable releases: tag `vX.Y.Z`. To release to Central automatically, run the workflow manually (*Run workflow*) with `auto_release` enabled.

## Releasing the catalog apps (milestones only)

```bash
git tag -a catalog-v1.0.0 -m "Catalog 1.0.0" && git push origin catalog-v1.0.0
```

The Android APK is debug-signed and named `-unsigned` until the keystore secrets exist; desktop installers are unsigned and not notarised until the Apple secrets exist. Nothing else is needed: no Central secrets are used by this workflow.

## Local equivalents

```bash
./gradlew publishToMavenLocalAll                 # all libraries + Gradle plugins into ~/.m2
cd samples/consumer && ./gradlew desktopTest     # consume them like an external project would
./gradlew :catalog:desktopApp:run                # run the catalog
./gradlew :catalog:androidApp:assembleRelease    # APK (debug-signed without ANDROID_KEYSTORE_PATH)
```

Published artifacts: `tech.kloos.kompound:kompound`, `kompound-annotations`, `kompound-demo`, `kompound-processor`, `kompound-gradle-plugin` (plus plugin markers `tech.kloos.kompound.demos` and `tech.kloos.kompound.catalog`).
