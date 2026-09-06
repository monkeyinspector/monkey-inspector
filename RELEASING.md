# Releasing to Maven Central

The project publishes these coordinates:

```text
io.github.monkeyinspector:monkeyinspector:0.3.0
```

## Prerequisites

1. Verify the `io.github.monkeyinspector` namespace in the
   [Central Portal](https://central.sonatype.com/).
2. Generate a Central Portal user token.
3. Import a GPG signing key into the local keyring and publish its public key
   to a public keyserver.
4. Set these environment variables without committing their values:

```text
JRELEASER_MAVENCENTRAL_APP_USERNAME
JRELEASER_MAVENCENTRAL_APP_PASSWORD
```

The Gradle signing plugin uses the local `gpg` command and keyring.

## Prepare and verify

On Windows:

```powershell
.\gradlew.bat clean test javadoc publishAllPublicationsToStagingRepository
.\gradlew.bat jreleaserConfig
```

Inspect `build/staging-deploy` before upload. It must contain the main JAR,
sources JAR, Javadoc JAR, POM, Gradle module metadata, and an `.asc` signature
for every published artifact.

## Publish

The following command uploads and publishes the staged repository through the
Central Publisher Portal. Do not run it until the version, coordinates,
license, generated POM, signatures, and release notes have been reviewed.

```powershell
.\gradlew.bat jreleaserDeploy
```

Maven Central releases are immutable. After the deployment reaches
`PUBLISHED`, create and push the matching Git tag:

```powershell
git tag -a v0.3.0 -m "Monkey Inspector 0.3.0"
git push origin v0.3.0
```
