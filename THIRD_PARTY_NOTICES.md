# Third-party materials

Dada Blubb's GPL-3.0-only grant covers the author's original contributions only.
Dependencies and any third-party materials retain their respective licenses and
notices. The dependency declarations and versions remain in
`gradle/libs.versions.toml` and `app/build.gradle.kts`.

## Dependency review

The review-fix pass resolved `releaseRuntimeClasspath` offline using the declared
dependencies: **109 modules and 75 JAR/AAR artifacts**, with locally cached Maven
POMs for all 109 modules. `./gradlew --offline --no-configuration-cache
:app:dependencies --configuration releaseRuntimeClasspath` reproduces the graph.
The inspected metadata lists
Apache License 2.0 for AndroidX (including Compose, Activity, Lifecycle and Core),
Kotlin standard library, kotlinx.serialization and kotlinx.coroutines. Of those
109 modules, 108 declared Apache 2.0. The POM for the transitive dependency
`com.google.guava:listenablefuture:1.0` has no license declaration in its POM,
but its upstream source is **Apache-2.0**, not an unlicensed/unknown dependency.
The exact [Maven Central 1.0 sources artifact](https://repo.maven.apache.org/maven2/com/google/guava/listenablefuture/1.0/listenablefuture-1.0-sources.jar)
contains `com/google/common/util/concurrent/ListenableFuture.java` with
`Copyright (C) 2007 The Guava Authors` and an Apache License, Version 2.0 header.
The [upstream versioned source](https://github.com/google/guava/blob/v27.0/futures/listenablefuture1/src/com/google/common/util/concurrent/ListenableFuture.java)
provides the same provenance. The exact sources JAR has SHA-256
`3d1bd8d4a39b293612a40e547ec51d9ce34fa638d7adeae83871cdbe2923b161`.
The cached 1.0 binary JAR and exact sources JAR contain no separate LICENSE/NOTICE
entry. This project preserves the upstream
attribution here and includes the Apache 2.0 text below; it does not relicense
Guava. These
components are developed by their respective Android Open Source Project,
Google, JetBrains and other contributors. The Apache 2.0 text is included in
[`third_party/APACHE-2.0.txt`](third_party/APACHE-2.0.txt); upstream copyright and
NOTICE files remain applicable. This is an inventory of the inspected families,
not a replacement for upstream notices or a claim to relicense their code.
An archive inspection of the 75 resolved release JAR/AAR files, including AAR
`classes.jar` contents, found no separate NOTICE-named entries. This does not
prove that every upstream attribution obligation is captured: final optimized
release artifacts, sources not embedded in those archives and future dependency
changes still need a distribution-time review. No signed release was built.

JUnit and AndroidX Test / Espresso are test-only dependencies. JUnit uses the
Eclipse Public License 1.0; AndroidX Test and Espresso use Apache 2.0. Build tools
are development dependencies, not original project material licensed by this
repository. Recheck the resolved **release** dependency set and its upstream
NOTICE obligations when preparing a public binary; this milestone does not
publish a release or claim a complete automated license audit.

## Artwork and fonts

The Grottenolm / “di blubb” image in `app/artwork/DadaBlubb.png`, its WebP
launcher derivatives and the existing adaptive icon resources are preserved.
The icon was created using OpenAI image generation based on Jochen Schmitt's
instructions. No third-party logo, stock-art credit, signature or external font
file was identified in the shipped resources during inspection. Compose uses
system fonts; no TTF/OTF font assets are bundled.

The project licenses original artwork contributions only to the extent the
author holds applicable rights. This does not assert copyright protection for
AI-generated elements where the law does not grant it. The visual inspection and
provided provenance do not establish an independent chain of title for every
image element; no separate source artwork or third-party attribution was supplied.
