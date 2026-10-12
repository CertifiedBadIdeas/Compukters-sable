# Compukters: Sable

Construction pose, velocity and mass properties observation and peripheral networks on Sable constructions for Minecraft 1.21.1.

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
Addon 2.0 requires the Compukters 0.6.0 SDK; use workspace mode until that base release is published.
This downloads the exact public GitHub SDK selected by `compuktersRelease`, verifies its SHA256 and uses
its local file repository. No GitHub token or external Compukters Maven service is needed.
An unpacked SDK can be supplied with `-PcompuktersDependencyMode=release -PcompuktersSdkDirectory=/path/to/sdk`.
The downloadable SDK is available starting with Compukters 0.5.1.
There is no automatic fallback between source and released dependencies.

Only pushed version tags publish this addon. Its release contains the production JAR, named development JAR,
Guest API bundle and SHA256 inventory. Ordinary commits produce no GitHub Releases.

The Guest physics API uses value snapshots and the shared stdlib math types: `Vector3d` for vectors,
`Rotation3d` for validated orientation and row-major `Matrix3d` for inertia. Read raw quaternion components
through `snapshot.orientation.quaternion`; use tensor components `m00` through `m22` or matrix/vector operations.
This replaces the earlier PhysicsVector, PhysicsRotation and PhysicsInertiaTensor API.
