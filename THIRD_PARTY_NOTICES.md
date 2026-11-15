# Third-party materials

Dada Blubb's GPL-3.0-only grant covers the author's original contributions only.
Dependencies and any third-party materials retain their respective licenses and
notices. The dependency declarations and versions remain in
`gradle/libs.versions.toml` and `app/build.gradle.kts`.

## Dependency review

All 109 resolved release dependency modules had locally cached Maven POMs. The
metadata inspected for this milestone lists
Apache License 2.0 for AndroidX (including Compose, Activity, Lifecycle and Core),
Kotlin standard library, kotlinx.serialization and kotlinx.coroutines. Of those
109 modules, 108 declared Apache 2.0. The POM for the transitive dependency
`com.google.guava:listenablefuture:1.0` has no license declaration; verify its
upstream license/notice before public binary distribution. It has not been
relicensed by this project. These
components are developed by their respective Android Open Source Project,
Google, JetBrains and other contributors. The Apache 2.0 text is included in
[`third_party/APACHE-2.0.txt`](third_party/APACHE-2.0.txt); upstream copyright and
NOTICE files remain applicable. This is an inventory of the inspected families,
not a replacement for upstream notices or a claim to relicense their code.

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
