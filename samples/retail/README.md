# Retail sample (Android)

Placeholder for a retail content sample built on `metabind-content-android`, mirroring
the `Retail` sample on the Apple side. This directory is currently a stub — there is no
buildable app here yet, so `:samples:retail:assembleDebug` does not apply.

When the sample is implemented it will follow the same pattern as the other samples: its
own Gradle build under `samples/retail/`, building against the in-tree SDK via
`includeBuild("../..")` with a dependency substitution onto `:metabind-content`.
