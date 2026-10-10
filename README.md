# Compukters: Sable

Independent construction pose and velocity observation for Minecraft 1.21.1.

Installation, upstream versions, Guest Kotlin examples, device lifetime and development commands are maintained in the
[canonical Wiki guide](https://certifiedbadideas.github.io/Compukters/SABLE/).

See [addon versioning](https://certifiedbadideas.github.io/Compukters/ADDON-DEVELOPMENT/#first-party-addon-versions)
for the independent `x.y` API/update version and Compukters target line in production JAR names. For combined
Create/Sable/Propulsion and Aeronautics development, use the
[all-addon stand](https://certifiedbadideas.github.io/Compukters/DEV-STAND/).


## Build dependencies

Local co-development explicitly uses `compuktersDependencyMode=workspace`. In the pinned main workspace,
run `./gradlew stageAddonDevelopmentDependencies` in Compukters first, then build this addon with `./gradlew build`.
For a separate checkout, pass `-PcompuktersWorkspace=/path/to/Compukters`.

To build without the main sources, use `./gradlew build -PcompuktersDependencyMode=release`.
This downloads the exact public GitHub SDK selected by `compuktersRelease`, verifies its SHA256 and uses
its local file repository. No GitHub token or external Compukters Maven service is needed.
An unpacked SDK can be supplied with `-PcompuktersDependencyMode=release -PcompuktersSdkDirectory=/path/to/sdk`.
The first SDK archive will be published with Compukters 0.6.0; use workspace mode until then.
There is no automatic fallback between source and released dependencies.

Only pushed version tags publish this addon. Its release contains the production JAR, named development JAR,
Guest API bundle and SHA256 inventory. Ordinary commits produce no GitHub Releases.
