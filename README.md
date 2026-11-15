# Dummlaberer

Dummlaberer 1.1.0 is an offline Android playground for absurd, rhythmic,
German-sounding fantasy words, built with Kotlin and Material 3 Jetpack Compose.
The English interface follows the system's light/dark theme and supports Android
dynamic colors.

## Features

- Exact Word Count from 1 to 50 unique stems, using synchronized buttons and a
  slider. Small imported datasets limit the maximum to their distinct stem count.
- One continuous fantasy string in an editable, selectable, multiline text field.
  Long results scroll inside the field; Copy and the Android Sharesheet use the
  current edited content. Generate replaces it and clears editor focus.
- Mixed, Rhythmic, and Chaotic styles, plus repetition and connector controls.
- Persistent Word Count and settings; generated and edited output survives rotation within
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
daemon. The toolchain and all pinned build dependencies must already be locally
available for offline verification; the installed app works fully offline.

## Build and run

Open the repository in Android Studio, sync Gradle, and run the `app`
configuration on an emulator or device. Alternatively, from the repository root:

Linux:

```sh
./gradlew --offline assembleDebug
./gradlew --offline installDebug
```

Windows Command Prompt:

```bat
gradlew.bat --offline assembleDebug
gradlew.bat --offline installDebug
```

`installDebug` requires a connected device/emulator with debugging enabled.
The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Usage

1. Set **Word Count** with the slider or precise − / + controls.
2. Tap **Generate**. First launch shows a placeholder until you generate.
3. Tap the output field to edit, add lines, or select text. The keyboard opens
   when editing, without an automatic focus request on launch or generation.
   **Copy** / **Share** use the entire current field, including edits and newlines.
   These actions are disabled when the field is empty. **Generate** replaces edits.
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

Sound selection uses small, computed German-oriented heuristics in
`core/SoundProfile.kt`; there are no manual rhyme lists or new JSON fields.
The original 449 stems and their export spelling remain unchanged. A temporary
sound key folds case, Unicode accent encoding and umlauts broadly (`ä` to `e`,
`ö` to `o`, `ü` to `u`), common spellings (`ph/f`, `th/t`, `ck/k`, `tz/z`,
`x/ks`, `qu/kv`, `w/v`, `sch`, `ch`, `ai/ay/ey/ei`, `ie/i`), doubled letters,
and final `b/p`, `d/t`, `g/k`. These are deliberately approximate sound families,
not a pronunciation dictionary; vowel length and stress are not modeled.

Each key supplies its initial consonant group, first vowel group, final vowel
group plus trailing consonants (the echo), and vowel-group count (approximate
syllables). For example, `Fex` and `Pheks` can echo despite different last two
letters; `Schnurpsel` and `Schnuff` share an onset/vowel cluster. Vowelless and
non-German imports remain valid and fall back to broad selection.

| Style | Echo attempt: Normal / High | Cluster preference | Short/long pulse preference |
| --- | ---: | ---: | ---: |
| Mixed | 35% / 80% | 45% | 45% |
| Rhythmic | 75% / 92% | 85% | 85% |
| Chaotic | 5% / 65% | Contrasting onset and sound family | None |

These are conditional selection probabilities, not promised output frequencies.
First, a soft rhythm preference narrows the unused pool to two vowel groups after
a one-group stem, or one group after a longer stem. If unavailable, it prefers
one/two-group stems, then falls back to all unused stems. Broad draws keep longer
stems eligible. Echo selection then chooses an earlier sound that still has an
unused match in that pool. When older matches exist, Normal chooses a dedicated
distant-target pool on 60% of attempts and High on 85%; other target draws use
all eligible earlier sounds. The distant pool prefers an ending contrasting with
the neighbor. After two neighboring stems with the same echo, the next echo-attempt probability is multiplied by 0.45 to
encourage a change. There is no fixed rhyme sequence.

When an echo is not chosen or unavailable, Mixed and Rhythmic can favor the
previous onset plus first vowel with a different ending; Chaotic favors a
different onset and sound family. Empty preferred pools fall back to the available
unused stems. High repetition increases sound echoes without repeating identities.
The rhythm bias is useful especially for counts 5–10, while all counts 1–50
retain exact size and uniqueness. Results depend on the imported vocabulary;
small or uniform vocabularies naturally offer fewer sound contrasts. Selection
uses the existing injectable/seedable RNG without retry loops over used stems.
There is no separate Poetry Mode.

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
./gradlew --offline testDebugUnitTest assembleDebug :app:compileDebugAndroidTestKotlin
```

Windows:

```bat
gradlew.bat --offline testDebugUnitTest assembleDebug :app:compileDebugAndroidTestKotlin
```

JVM tests cover counts 1–50 and normalized uniqueness in all settings
combinations, small imports and adaptive count limits, repeated connectors and boundaries,
empty connectors, deterministic weight tickets and distributions, different mode
and repetition behavior, phonetic spelling variants, onset/vowel clusters, distant
echoes, flexible short/long pulses for counts 5–10, exhausted/vowelless sound
pools, long output, Unicode, settings restoration, the bundled asset, malformed/schema/size/encoding/overflow validation, atomic write failures,
corrupt override recovery, and reset.
Bundled-data regression tests expect 449 normalized unique stems and connector
weights `di:60`, `de:5`, `da:5`, `didel:5`, `eldi:5`, and empty text `:20`. They
exercise every allowed count (1–50) with all settings combinations and verify
fresh default loading, preserved imports, and explicit reset using the real asset.

Android tests cover Compose count controls, empty/nonempty actions, complete
50-stem output, multiline editing and clearing, Copy/Share receiving edits,
Generate replacing edits without focusing the editor, activity recreation,
radio selection, preference persistence, and reset retaining settings. Clipboard
content and the actual chooser/send intent payload are checked at the app boundary;
no external receiving app is needed. Tests use isolated preferences/files where
persistence is modified. A one-stem vocabulary has disabled count controls and
an explanatory limit.

Use **Medium Phone** (internal AVD name `medium_phone`) for Android verification;
never default to Medium Tablet or a physical device. Inspect before starting:

```sh
"$ANDROID_HOME/emulator/emulator" -list-avds
"$ANDROID_HOME/platform-tools/adb" devices -l
# Start medium_phone only if it is not already running, and wait for boot.
# Resolve each emulator's identity with: adb -s <serial> emu avd name
# Check: adb -s <phone-serial> shell getprop sys.boot_completed  # must be 1
ANDROID_SERIAL=<phone-serial> ./gradlew --offline connectedDebugAndroidTest
```

`ANDROID_SERIAL` must contain the dynamically discovered Medium Phone serial.
The connected task installs the app and test APK only on the selected emulator.
Shut down with `adb -s <phone-serial> emu kill` only if you started that emulator.
The same wrapper tasks work from Windows with `gradlew.bat` and the appropriate
shell environment syntax. Offline builds require all pinned artifacts already
cached; do not change dependency versions to make a release build pass.

Reports: `app/build/reports/tests/testDebugUnitTest/`,
`app/build/reports/androidTests/connected/debug/`, and
`app/build/reports/lint-results-debug.html`.

Verification on the Debian NUC, 2026-11-15:

The pinned offline command
`./gradlew --offline --no-configuration-cache testDebugUnitTest assembleDebug :app:compileDebugAndroidTestKotlin`
failed during dependency resolution: Serialization JSON 1.9.0 is absent from the
local cache. The explicitly targeted `:app:connectedDebugAndroidTest` command
failed at the same prerequisite before installation. A subsequent cache-only
attempt also identified a missing Coroutines Test JVM 1.9.0 JAR.

For supplemental source verification, a temporary local init script at
`/tmp/dummlaberer-offline-compat.init.gradle` selected cached Serialization 1.8.1
and Coroutines 1.10.2. No project dependency declarations were changed and no
artifacts were downloaded. With
`./gradlew --offline --no-configuration-cache -I /tmp/dummlaberer-offline-compat.init.gradle`:

| Tasks | Result with local compatibility artifacts |
| --- | --- |
| `testDebugUnitTest` | 46 passed; no failures or skips |
| `assembleDebug` | Successful |
| `:app:compileDebugAndroidTestKotlin` | Successful |
| `:app:connectedDebugAndroidTest` | 8 passed; no failures or skips |

The Android task additionally used `ANDROID_SERIAL` and
`-Pandroid.injected.device.serial` with the serial discovered from the running
Medium Phone AVD. The app and test APK were installed on that booted Android 16
(API 36) phone emulator. The agent started and shut down this emulator; no tablet
or physical device was used. The final emulator process exited successfully and
ADB reported no remaining devices. These results cover compatibility artifacts;
the current debug APK uses those artifacts and a build with pinned dependencies
remains unverified. Human listening and external document/share providers remain
manual acceptance items below.

Manual acceptance for v1.1.0 (human listening and external provider checks remain):

1. Generate with counts 5–10 and 50; verify there are no repeated stems. Check
   the centered controls, slider spacing, selection, complete wrapping, large
   fonts, landscape, and TalkBack labels/touch targets. Try an import with only
   one or two distinct stems and check its count limit cannot be exceeded.
2. Try Rhythmic style with High repetition: sounds may echo while stems stay
   unique. Rotate, switch light/dark theme, and relaunch; check saved settings.
3. Edit multiple lines and a long result, then copy and paste into another app;
   share the edits through the actual system Sharesheet. Generate again to replace
   edits, and rotate to check text retention.
4. Export, edit, import, cancel once, then confirm. Generate from the import and
   export again. Reject malformed JSON without replacing the previous dataset.
5. Reset and check defaults return while settings remain. Check document picker
   cancellation and destination replacement on the device's provider.
6. Verify the three-dot menu order and About, including visible version
   `1.1.0` (versionCode 2).

The debug APK is for manual acceptance and is not a published release artifact.
Commits, tags, pushes, and release publication require an explicit user request.

## Privacy and offline behavior

No INTERNET or storage permission, backend, analytics, ads, or network calls.
Generation, validation, and private persistence work offline. Clipboard, sharing,
and document providers are Android system features; other apps/providers may
have their own network behavior. Android system backups follow the existing
manifest backup configuration and device settings.
