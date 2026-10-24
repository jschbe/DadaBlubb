# Dummlaberer

Dummlaberer 1.0.0 is an offline Android playground for absurd, rhythmic,
German-sounding fantasy words, built with Kotlin and Material 3 Jetpack Compose.
The English interface follows the system's light/dark theme and supports Android
dynamic colors.

## Features

- Exact Word Count from 1 to 50 unique stems, using synchronized buttons and a
  slider. Small imported datasets limit the maximum to their distinct stem count.
- One continuous fantasy string with selectable, wrapping output, Copy, and the
  Android Sharesheet. Long results scroll without truncation.
- Mixed, Rhythmic, and Chaotic styles, plus repetition and connector controls.
- Persistent Word Count and settings; generated output survives rotation within
  the current process but is not saved across process death.
- One curated default asset containing 449 unique stems and weighted connectors.
- Complete dataset import/export through Android's document picker and a
  confirmed reset that keeps settings.
- About displays the installed version from Android package build metadata.

The supplied launcher artwork is preserved in
[`app/artwork/Dummlaberer.png`](app/artwork/Dummlaberer.png). Android resources
include adaptive icons with space for launcher masks and raster icons at all
five standard densities. The adaptive icons retain the full-color artwork.

## Screenshots

Screenshots have not yet been added. Suggested views: the main screen with a
generated result, settings, and import confirmation, in light and dark themes.

## Requirements

- Android 9 / API 28 or newer to run the app.
- Android Studio supporting the checked-in AGP 9.4.1 and Gradle 9.6.0 wrapper,
  with a compatible JDK configured for Gradle.
- Android SDK API 37 (`platforms;android-37.0`) and AGP's selected build tools.
- Existing Kotlin 2.2.10, compileSdk 37, targetSdk 37, minSdk 28, and package
  `com.github.jschbe.dummlaberer` are preserved.

The checked-in `gradle/gradle-daemon-jvm.properties` selects JDK 25 for the Gradle
daemon. The development machine may need network access to download that
toolchain and build dependencies; the installed app works offline.

## Build and run

Open the repository in Android Studio, sync Gradle, and run the `app`
configuration on an emulator or device. Alternatively, from the repository root:

Linux:

```sh
./gradlew assembleDebug
./gradlew installDebug
```

Windows Command Prompt:

```bat
gradlew.bat assembleDebug
gradlew.bat installDebug
```

`installDebug` requires a connected device/emulator with debugging enabled.
The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Usage

1. Set **Word Count** with the slider or precise − / + controls.
2. Tap **Generate**. First launch shows a placeholder until you generate.
3. Long-press to select output, or use **Copy** / **Share** for the full result.
   These actions are disabled when output is empty.
4. The three-dot menu contains **Settings**, **Import JSON**, **Export JSON**,
   **Reset to Defaults**, and **About**, in that order.

Settings save automatically and apply to the next generation. Changing settings
or vocabulary does not rewrite an existing result.

## Generator rules

**Word Count counts stems, not characters or connectors.** A generation with
count N selects exactly N unique stems from the active vocabulary without
replacement. Stem identity ignores case and surrounding whitespace and uses
Unicode NFC normalization, so composed/decomposed accents identify the same stem.
No stem can appear twice anywhere in one output. Text is never cropped to a
character budget, and stems join into one string without spaces.

The Word Count controls form a compact centered − / number / + group, separated
from the slider by 24 dp. When an import has fewer than 50 distinct stems, the
controls show that smaller maximum and explain the limit. The current count is
clamped and saved on load/import; it is never silently shortened during
generation. A one-stem dataset fixes the count at 1 and disables the slider and
both count buttons. Other settings remain unchanged. The core generator rejects
an out-of-range request if a caller bypasses the UI's cap.

Each of the N−1 adjacencies independently chooses one weighted connector entry.
An empty connector means direct concatenation. A nonempty entry inserts one
connector. Connectors never appear before the first stem or after the last;
N=1 always produces one stem and no connector. Connectors may repeat freely,
including `di`; stem uniqueness does not restrict connector selection.

Display lowercases the assembled string with the German locale and titlecases
its first Unicode code point. Umlauts, accents, and supplementary Unicode letters
remain valid. Export retains original vocabulary spelling.

Repetition refers to sounds rather than reusing stems. The algorithm uses these
probabilities of favoring an unused stem whose last two letters match a randomly
chosen earlier stem:

| Style | Normal repetition | High repetition | Otherwise |
| --- | ---: | ---: | --- |
| Mixed | 35% | 80% | Broad selection among remaining stems |
| Rhythmic | 75% | 95% | Favor a matching first letter for alliteration |
| Chaotic | 5% | 65% | Favor a different first letter and different last two letters |

Every selection comes from the remaining unused identities. An empty preferred
sound pool falls back to the style's alternatives, then to any remaining stem.
High repetition increases sound echoes while preserving uniqueness and N. Sound
patterns depend on what the active vocabulary contains; matching sounds cannot
be preferred once their unused stems run out. Selection uses the existing
injectable/seedable RNG and never retries used stems in a rejection loop.

Connector **Normal** uses JSON weights directly. **Frequent** triples nonempty
weights while preserving their relative proportions. With the default weights,
nonempty probability rises from 80% to about 92.3%. When all entries are empty
or all are nonempty, that probability cannot increase. `di` dominates bundled
defaults; imports honor their supplied weights.

## JSON format and default vocabulary

The single canonical, Git-tracked default dataset is
[`app/src/main/assets/dada.json`](app/src/main/assets/dada.json). Edit it to change
defaults in future builds. Runtime imports never modify the asset. It includes
Möter, Röngel, Döngel, Rubbel, Nuddel, Wachs, Blogg, Schnurpsel, Knöter, Bummel,
and Pömpel. Example schema v1:

```json
{
  "schemaVersion": 1,
  "words": ["Möter", "Röngel", "Döngel", "Rubbel", "Nuddel", "Wachs", "Blogg"],
  "connectors": [
    {"text": "di", "weight": 60},
    {"text": "de", "weight": 5},
    {"text": "da", "weight": 5},
    {"text": "didel", "weight": 5},
    {"text": "eldi", "weight": 5},
    {"text": "", "weight": 20}
  ]
}
```

Validation rejects invalid content with English errors:

- UTF-8 only, at most 1 MiB, including the formatted export representation.
- `schemaVersion` must be integer 1. Only documented fields are accepted.
  Duplicate object keys, comments, trailing commas, and malformed JSON are rejected.
- 1–5,000 distinct words, each 1–64 Unicode code points.
  Existing import validation still rejects exact duplicates and whitespace.
  Case/accent-encoding variants remain importable and export unchanged, but count
  as a single available stem for selection and the Word Count maximum.
- 1–128 connectors with distinct text values, each 0–32 Unicode code points.
  Empty connector text is valid.
- Words and nonempty connectors start with a letter and contain only Unicode
  letters and combining accents. No whitespace, punctuation, controls, invisible
  formatting, or unpaired surrogates. Entries are never silently trimmed,
  normalized, or deduplicated.
- Weights must be strictly positive integers; their total must not exceed
  2,147,483,647. Relative weights need not sum to 100. Frequent mode uses Long
  arithmetic to avoid multiplication overflow.

### Import, export, and reset

**Import JSON** opens a single document. Parsing and validation occur before a
confirmation showing word and connector counts. **Replace** activates the
entire dataset immediately; **Cancel** keeps it unchanged. Failed reading,
validation, or writing retains the previous active dataset.

Overrides live in app-private storage. Replacement writes and syncs a temporary
file in the same directory, then atomically replaces the override. Memory changes
only after successful persistence. An unreadable saved override triggers an
English warning and fallback to defaults; import again or reset to repair it.

**Export JSON** suggests `dummlaberer.json` and writes the currently active
complete dataset as human-readable UTF-8 JSON, including weights. It exports
vocabulary, not generated output. Android's document provider handles destination
selection and replacement of an existing document.

**Reset to Defaults** requires confirmation, discards the override, and reloads
the bundled asset. Word Count, style, repetition, and connector settings remain.
Successful imports, exports, and resets show a confirmation.

A fresh install uses the bundled asset. Updating the APK keeps a valid imported
override active; it does not silently replace custom vocabulary. To try revised
bundled defaults on a device with an import, export the custom dataset first if
you want to keep it, then choose **Reset to Defaults** and confirm. Export again
to inspect the active words and weights.

## Implementation and dependencies

The app remains one module. `core/` holds Android-independent settings,
generation, structured results, and JSON validation. `data/` uses JVM file
operations for datasets and SharedPreferences for the four small preferences.
An Android ViewModel retains state through rotation and runs file/document work
on `Dispatchers.IO`. There is no DI framework or database.

Added dependencies are Lifecycle ViewModel at the existing Lifecycle version and
`kotlinx-serialization-json` 1.9.0. This JSON parser works on Android and the JVM
without a serialization compiler plugin; its [release notes](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.9.0)
identify Kotlin 2.2 compatibility. SharedPreferences avoids a DataStore dependency
for four small preferences. Existing SDK and build tool versions are unchanged.

## Tests and verification

Linux:

```sh
./gradlew testDebugUnitTest assembleDebug :app:compileDebugAndroidTestKotlin
```

Windows:

```bat
gradlew.bat testDebugUnitTest assembleDebug :app:compileDebugAndroidTestKotlin
```

JVM tests cover counts 1/2/8/50 and normalized uniqueness in all settings
combinations, small imports and adaptive count limits, repeated connectors and boundaries,
empty connectors, deterministic weight tickets and distributions, different mode
and repetition behavior, long output, Unicode, settings restoration, the bundled
asset, malformed/schema/size/encoding/overflow validation, atomic write failures,
corrupt override recovery, and reset.
Bundled-data regression tests expect 449 normalized unique stems and connector
weights `di:60`, `de:5`, `da:5`, `didel:5`, `eldi:5`, and empty text `:20`. They
exercise every allowed count (1–50) with all settings combinations and verify
fresh default loading, preserved imports, and explicit reset using the real asset.

Android test sources cover Compose count controls, empty/nonempty actions,
complete 50-stem output passed to callbacks, radio selection, real preference
persistence, and reset retaining settings. They use isolated test state and
preferences/files. The Android suite also covers a one-stem vocabulary with
disabled count controls and an explanatory limit. Its sources are compiled as
part of verification; device/UI tests were not rerun for the uniqueness and
layout refinement. The earlier four-test Android suite passed on a Pixel 7 Pro
running Android 17 before this refinement.
To run device tests, use `./gradlew connectedDebugAndroidTest`
on Linux or `gradlew.bat connectedDebugAndroidTest` on Windows with a connected
device/emulator. This installs the app and test APK.

Reports: `app/build/reports/tests/testDebugUnitTest/`,
`app/build/reports/androidTests/connected/debug/`, and
`app/build/reports/lint-results-debug.html`.

Manual acceptance for v1.0.0 (not yet completed for this build):

1. Generate with counts 5–10 and 50; verify there are no repeated stems. Check
   the centered controls, slider spacing, selection, complete wrapping, large
   fonts, landscape, and TalkBack labels/touch targets. Try an import with only
   one or two distinct stems and check its count limit cannot be exceeded.
2. Try Rhythmic style with High repetition: sounds may echo while stems stay
   unique. Rotate, switch light/dark theme, and relaunch; check saved settings.
3. Copy and paste into another app; share through the actual system Sharesheet.
4. Export, edit, import, cancel once, then confirm. Generate from the import and
   export again. Reject malformed JSON without replacing the previous dataset.
5. Reset and check defaults return while settings remain. Check document picker
   cancellation and destination replacement on the device's provider.
6. Verify the three-dot menu order and About, including visible version `1.0.0`.

The debug APK is for manual acceptance and is not a published release artifact.
Only after the user explicitly approves manual results should a separate release
step create the v1.0.0 commit and annotated tag. Pushing remains the user's
responsibility.

## Privacy and offline behavior

No INTERNET or storage permission, backend, analytics, ads, or network calls.
Generation, validation, and private persistence work offline. Clipboard, sharing,
and document providers are Android system features; other apps/providers may
have their own network behavior. Android system backups follow the existing
manifest backup configuration and device settings.
