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
4. Copy `gradle.properties.example` to the ignored `gradle.properties` file
   and fill in the Central user-token credentials and GPG passphrase:

```text
mavenCentralUsername=central-token-username
mavenCentralPassword=central-token-password
signing.keyId=D23E465D
signing.password=gpg-passphrase
signing.secretKeyRingFile=C:/path/to/secring.gpg
```

The same properties may instead be stored in
`~/.gradle/gradle.properties` so they can be shared by local projects without
entering this repository.

## Prepare and verify

On Windows:

```powershell
.\gradlew.bat clean test javadoc publishToMavenLocal
```

Inspect the generated publication in the local Maven repository. It must
contain the main JAR, sources JAR, Javadoc JAR, POM, Gradle module metadata,
and an `.asc` signature for every published artifact.

## Publish

The following command uploads and publishes the staged repository through the
Central Publisher Portal. Do not run it until the version, coordinates,
license, generated POM, signatures, and release notes have been reviewed.

```powershell
.\gradlew.bat publishToMavenCentral
```

Maven Central releases are immutable. After the deployment reaches
`PUBLISHED`, create and push the matching Git tag:

```powershell
git tag -a v0.3.0 -m "Monkey Inspector 0.3.0"
git push origin v0.3.0
```
