# Dada Blubb

Dada Blubb 1.2.0 is an offline Android playground for absurd, rhythmic,
German-sounding fantasy words, built with Kotlin and Material 3 Jetpack Compose.
The interface supports German and English, persistent System default / Light /
Dark themes, and Android dynamic colors. Gradle's root project is **DadaBlubb**;
the Kotlin package, namespace and applicationId are **`com.github.jschbe.dadablubb`**.
The public repository is
[github.com/jschbe/DadaBlubb](https://github.com/jschbe/DadaBlubb).
The local checkout directory can retain its existing name.

## Installation identity and existing data

This pre-upload naming cleanup changes the historical applicationId
`com.github.jschbe.dummlaberer` to `com.github.jschbe.dadablubb`. Android treats it
as a **separate installation**, not an update of the old app, even though the
visible name and version **1.2.0 / code 3** are unchanged. Keep the old app and
its data. Nothing is automatically migrated, reset, cleared or uninstalled.
The new app starts with the bundled vocabulary and its own preferences. If custom
vocabulary is needed, separately export it from the old app and explicitly import
it into the new one. Generated/edited output can be copied or shared separately;
settings are independent. Future upgrades using the new applicationId preserve
the new app's data in the usual way.

## Features

- Exact Word Count from 1 to 50 unique stems, using synchronized buttons and a
  slider. Small imported datasets limit the maximum to their distinct stem count.
- One continuous fantasy string in an editable, selectable, multiline text field.
  Long results scroll inside the field; Copy and the Android Sharesheet use the
  current edited content. Generate replaces it and clears editor focus.
- Mixed, Rhythmic, and Chaotic styles, plus repetition and connector controls.
- Persistent Word Count, settings and generated/edited output, including intentionally
  empty output, across activity recreation and complete app restarts.
- One curated default asset containing 449 unique stems and weighted connectors.
- Complete dataset import/export through Android's document picker and a
  confirmed reset that keeps settings.
- Settings starts with **About App**, followed by **Appearance**, **Language**,
  and the existing generation settings. About shows installed versionName and
  versionCode, copyright, GPL-3.0-only, the Codex credit and a source link.

The supplied launcher artwork is preserved in
[`app/artwork/DadaBlubb.png`](app/artwork/DadaBlubb.png). Android resources
include adaptive icons with space for launcher masks and raster icons at all
five standard densities. The adaptive icons retain the full-color artwork.
The Grottenolm / “di blubb” icon was created using OpenAI image generation based
on the author’s instructions. The icon and artwork remain unchanged.

## Screenshots

Screenshots have not yet been added. Suggested views: the main screen with a
generated result, settings, and import confirmation, in light and dark themes.

## Requirements

- Android 9 / API 28 or newer to run the app.
- Android Studio supporting the checked-in AGP 9.4.1 and Gradle 9.6.0 wrapper,
  with a compatible JDK configured for Gradle.
- Android SDK API 37 (`platforms;android-37.0`) and AGP's selected build tools.
- Kotlin 2.2.10, compileSdk 37, targetSdk 37 and minSdk 28 are preserved.
  All source sets use package/namespace `com.github.jschbe.dadablubb`.

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
2. Tap **Generate**. The placeholder appears only until output has first been saved.
   Later launches restore the saved text, including an intentionally empty field.
3. Tap the output field to edit, add lines, or select text. The keyboard opens
   when editing, without an automatic focus request on launch or generation.
   **Copy** / **Share** use the entire current field, including edits and newlines.
   These actions are disabled when the field is empty. **Generate** replaces edits.
4. The three-dot menu contains **Settings**, **Import JSON**, **Export JSON**,
   and **Reset to Defaults**, in that order. **About App** is the first entry
   inside Settings; it is not duplicated in the menu.

Settings save automatically. Generation settings apply to the next generation;
changing them or the vocabulary does not rewrite the current result. Appearance
and language apply immediately. Copy/Share always use the entire edited output.

## Appearance, language, and state

Appearance uses three vertically stacked radio options: **System default**,
**Light**, and **Dark**. The default follows Android night mode; explicit choices
override it throughout Compose, including settings and dialogs. Dynamic colors
remain available on Android 12+. Appearance is stored in a separate `ui-settings`
SharedPreferences file, leaving the existing `generator-settings` keys intact.

Language offers **System default**, **Deutsch**, and **English** as vertically
stacked radios. System default uses German when the first active system language
is German; English is used for English and all unsupported languages. It does
not fall through to a secondary German locale when the first is unsupported.
Explicit choices override the system. Generated content and `dada.json` are never
translated. All interface text, accessibility labels, validation errors, dialogs,
feedback and About content come from `values/strings.xml` (English fallback) and
`values-de/strings.xml`.

`AppLocales` uses Android's `LocaleManager.applicationLocales` on API 33+, with
an empty list for System default. The framework persists the choice, exposes the
two supported languages in Android app-language settings, and recreates the
activity. Android's app-language selection is the source of truth on these
versions, including changes made outside the app. On API 28–32 a preference
persists the choice and the activity is recreated explicitly. An activity
configuration context resolves exactly `de` or `en` on every creation on both
paths, enforcing the first-system-language / English-fallback rule. No AppCompat
or other new runtime dependency is needed, and no global system setting changes.

The process-owned output repository retains generated/edited text; the ViewModel
retains pending imports, generator settings and structured feedback through
locale/configuration recreation. Navigation is
saved with Compose `rememberSaveable`. Theme changes need no activity recreation.
Output is now saved independently of the ViewModel as described below. Core validation
exposes typed diagnostic keys and arguments; the Android layer resolves them in
the current UI locale when shown, including errors that finish after a switch.
English diagnostic text remains available to standalone JVM callers and tests. Compose reads labels, semantics and callback text with
`stringResource`/`pluralStringResource` during composition; structured feedback
uses `LocalResources.current`. These reads observe configuration changes instead
of relying on a stable `LocalContext` identity. Android App Bundles disable
language splitting (`bundle.language.enableSplit = false`) so both UI languages
remain available offline after a Play installation.

## Saved output

The current generated or edited text is stored locally in `files/output.txt` as
UTF-8, capped at **64 KiB of encoded text**. New edits update the UI immediately.
Oversized changes and malformed Unicode are rejected with localized feedback;
accepted text is never silently truncated. Generate replaces and saves the output.
Copy and Share use the current editor text, including unsaved text after a write
failure. Vocabulary import/reset and generation-setting changes do not clear it.

A missing file is the first-start state. A zero-byte file is an intentionally
empty result: it remains blank after restart without showing the first-start
placeholder. Loading/error states are separate; a read failure shows an error
and leaves the saved file untouched until an explicit edit or Generate.

One process-owned repository serializes writes on an IO dispatcher independently
of activity/ViewModel cancellation. Pending changes are conflated to the latest
revision; older writes cannot finish after a newer write. Initial loading cannot
overwrite a later edit. Each completed write syncs a temporary file and atomically
replaces the previous file. Failure retains the prior saved file, keeps current
text editable, and shows localized feedback; edit or Generate retries. A process
kill or power loss before the latest write completes can still lose that pending
change. There is no promise of synchronous per-keystroke durability or recovery
of output lost before this feature was installed.

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
    {"text": "de", "weight": 2},
    {"text": "da", "weight": 6},
    {"text": "didel", "weight": 6},
    {"text": "eldi", "weight": 6},
    {"text": "", "weight": 20}
  ]
}
```

The bundled connector weights sum to **100**. This weight update preserves all
449 stems in their original order and leaves schema v1 and generator logic unchanged.

Validation rejects invalid content with localized English/German errors:

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
only after successful persistence. An unreadable saved override triggers a
localized warning and fallback to defaults; import again or reset to repair it.

**Export JSON** suggests `dada-blubb.json` and writes the currently active
complete dataset as human-readable UTF-8 JSON, including weights. It exports
vocabulary, not generated output. Complete schema validation, UTF-8 encoding and
size checks finish before opening the destination. Write and close failures show
an error warning that the destination may be incomplete; the active vocabulary
is unaffected. SAF providers do not offer universal atomic replacement. The
CreateDocument picker normally creates a new document (adding a name suffix for
collisions); some system/provider paths permit explicitly confirmed replacement.
Do not rely on a failed export preserving a preexisting destination.

**Reset to Defaults** requires confirmation, discards the override, and reloads
the bundled asset. Word Count, style, repetition, and connector settings remain.
Successful imports, exports, and resets show a confirmation.

A fresh install uses the bundled asset. Updating the APK with the same
applicationId keeps a valid imported override active; it does not silently replace custom vocabulary. To try revised
bundled defaults on a device with an import, export the custom dataset first if
you want to keep it, then choose **Reset to Defaults** and confirm. Export again
to inspect the active words and weights.

## Implementation and dependencies

The app remains one Kotlin/Compose module. `core/` contains Android-independent
generation, sound profiles, settings and JSON validation. `data/` handles dataset
files, bounded output persistence and small SharedPreferences stores. Dataset and
document IO runs through the ViewModel on `Dispatchers.IO`; output writes use a
separate process-owned IO scope. Namespace and applicationId are
`com.github.jschbe.dadablubb`. MinSdk 28, SDK/tool versions, generator behavior,
all 449 stems and original artwork are preserved. Default connector weights are
documented above. VersionName is **1.2.0**, versionCode **3**. No dependency was added or
substituted for this milestone. There is no backend, DI framework or database.

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
weights `di:60`, `de:2`, `da:6`, `didel:6`, `eldi:6`, and empty text `:20`. They
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
never default to Medium Tablet or a physical device. A physical phone may be
used when the user explicitly requests it. The initial v1.2.0 run used the Samsung
A51 under that one-time authorization; the later lint cleanup uses Medium Phone
only. Inspect before starting:

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

### Review fixes DB-01–DB-06 verification

Version **1.2.0 / code 3**, applicationId **`com.github.jschbe.dadablubb`**, minSdk,
dependencies, generator, bundled 449 stems/connector weights and artwork are
unchanged. Backup rules, export error handling, durable output and documentation
were updated. No release signing/AAB/Play installation was performed.

Regular declared dependencies, Gradle Wrapper, offline:

```sh
./gradlew --offline --no-configuration-cache testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin lintDebug assembleDebug :app:assembleDebugAndroidTest
./gradlew --offline --no-configuration-cache -q :app:dependencies \
  --configuration releaseRuntimeClasspath
```

| Check | Result |
| --- | --- |
| `testDebugUnitTest` | 68 passed, 0 failures/errors/skips; 18 new tests cover backup configuration, document stream failures, bounded atomic output, cold-store restoration and concurrent updates |
| `:app:compileDebugAndroidTestKotlin` | Successful, including the existing suite and four new isolated tests |
| `lintDebug` | Successful: 0 errors, 11 previously reviewed warnings, 0 hints; no baseline/suppressions added |
| `assembleDebug`, `:app:assembleDebugAndroidTest` | Successful |
| Release runtime dependency graph | Resolved offline: 109 modules, 75 JAR/AAR artifacts; see THIRD_PARTY_NOTICES.md |
| Isolated A51 instrumentation | 4 passed, 0 failures/skips: two provider tests and two Compose/lifecycle tests |

The 11 remaining warnings are `UnusedAttribute` (1), `AndroidGradlePluginVersion`
(2), `GradleDependency` (1), `NewerVersionAvailable` (2), `IconXmlAndPng` (2),
`MonochromeLauncherIcon` (2), and `UseKtx` (1). Their rationale remains in the
historical lint-cleanup table; no dependency or artwork changes were made.

This pass used only the explicitly authorized **Samsung Galaxy A51 SM-A515F,
Android 13 / API 33**, selected dynamically from `adb devices -l`. Both APKs were
installed with explicit `adb -s <discovered-A51-serial> install -r -t <apk>`.
Only these safe test classes were executed:

```sh
adb -s <discovered-A51-serial> shell am instrument -w -r -e class \
  com.github.jschbe.dadablubb.ReviewDocumentsTest,com.github.jschbe.dadablubb.ReviewOutputUiTest \
  com.github.jschbe.dadablubb.test/androidx.test.runner.AndroidJUnitRunner
```

The provider tests use Android `DocumentsContract`, a real wrapped
`ContentResolver`/`ParcelFileDescriptor`, and an **in-process fixture DocumentsProvider**
on API 29+. Export/import/content comparison/reset and missing/denied/malformed
input operate only on unique cache files. They do not exercise chooser grants,
third-party/cloud provider behavior or reset the active user vocabulary. JVM tests
also inject write and close failures. Compose tests use standalone UI and unique
cache/preferences: edited action payloads, empty-output restoration, Generate,
and a deliberately blocked write completing after `ViewModelStore.clear()`.

`connectedDebugAndroidTest` and the full existing activity-based suite were not
run on this populated phone: those tests modify app state, and Gradle-managed
deployment/cleanup was avoided to preserve the installed app. No emulator was
started, no app was uninstalled, and no app data was cleared.

ADB-driven checks of the installed **main app** confirmed Generate/edit, exact
saved text after Home/app switching and force-stop/cold launch, intentionally
empty output without the first-start placeholder, and Generate after clearing.
German/English switching, system language on the phone's English system locale,
light/dark/system themes, About in both languages, and output retention through
locale recreation were checked. Actual Copy was verified by Ctrl+V paste-back;
the Sharesheet preview contained the exact edited text and was canceled without
selecting a recipient. German app language and System default appearance were
restored. Active vocabulary, generator/UI preferences and saved output were
byte-for-byte identical before/after the final repeated upgrade, isolated test
run and launch. The first pre-install capture accidentally listed filenames
instead of hashes, so there is no byte-level before/after proof relative to the
start of this task. No test wrote to the active user vocabulary. Final launch
logs had no error/fatal lines for the app process and no app crash-buffer entry.
The app was left running on the A51.

Real cloud backup/D2D migration, API 28–32 locale execution, unsupported/German
system-locale device scenarios, external document-provider roundtrips, full-device
reboot and release/Play artifacts remain unverified. Process restart was tested
with force-stop/relaunch, not a device reboot. JVM tests cover deterministic
language fallback and storage reconstruction; they do not replace those device
scenarios. The configured cloud exclusions were checked in source and compiled
APK XML; no system backup/restore was triggered.

### Historical v1.2.0 verification (before lint and naming cleanup)

Use the declared dependency versions with `--offline`; no temporary substitute
versions are supported. The APK is `app/build/outputs/apk/debug/app-debug.apk`.
For an authorized USB phone, inspect `adb devices -l`, then confirm its model and
API using `adb -s <discovered-serial> shell getprop ro.product.model` and
`ro.build.version.sdk`. Target installation and tests explicitly:

```sh
adb -s <discovered-serial> install -r app/build/outputs/apk/debug/app-debug.apk
ANDROID_SERIAL=<discovered-serial> ./gradlew --offline --no-configuration-cache \
  -Pandroid.injected.device.serial=<discovered-serial> :app:connectedDebugAndroidTest
```

Use `install -r` for upgrades. Do not uninstall, clear application data, or confirm
destructive import/reset operations on a user's phone. Instrumented preferences
and storage tests use isolated fixtures.

This historical run predates the applicationId change and used the old app
installation. Current-identity verification is recorded under Naming cleanup below.

Initial verification on the Debian NUC (2026-11-15), using declared dependencies
only. The missing-cache limitations in this historical run are superseded by
the lint-cleanup verification below:

| Command / task | Exact result |
| --- | --- |
| `./gradlew --offline --no-configuration-cache testDebugUnitTest assembleDebug` | BUILD SUCCESSFUL; 50 JVM tests passed, 0 failures/errors/skips; debug APK built |
| `./gradlew --offline --no-configuration-cache :app:compileDebugAndroidTestKotlin` | Failed during dependency resolution: uncached `kotlinx-coroutines-test-jvm:1.9.0` JAR |
| `ANDROID_SERIAL=<discovered-A51-serial> ./gradlew --offline --no-configuration-cache -Pandroid.injected.device.serial=<discovered-A51-serial> :app:connectedDebugAndroidTest` | Blocked by the same compile prerequisite; instrumented tests did not execute |
| `./gradlew --offline --no-configuration-cache lintDebug` | Blocked at `:app:generateDebugAndroidTestLintModel` by the same missing test JAR |
| `./gradlew --offline --no-configuration-cache :app:dependencies --configuration releaseRuntimeClasspath` | Successful; local POM license metadata inspected for 109 resolved modules |

No substitutes, new dependencies or network downloads were used. Android test
sources were updated for localized fixtures and new UI preferences/About tests,
but could not be compiled or executed in this offline environment. Any older
Android test or lint reports in `app/build` are not v1.2.0 verification results.

Device checks used the USB Samsung Galaxy A51, model **SM-A515F**, Android **13** /
API **33**, after dynamically verifying its serial/model/API. The final APK was
installed with `adb -s <discovered-A51-serial> install -r …` and launched with
`am start -W`; package metadata confirmed version **1.2.0 / code 3**, minSdk 28,
unchanged applicationId, and the launcher label **Dada Blubb**.

Direct, ADB-driven checks on the real UI (separate from the blocked Compose suite)
confirmed:

- Correct Settings order, vertical radio groups and no duplicate About menu item.
- German/English changes, restored Settings navigation, and German plus Dark
  persistence after app force-stop/relaunch; Light, Dark and System default
  rendering, including readable About dialogs.
- About text/credits/source URL in both languages and actual installed version/code.
- Generate, multiline editing, Copy feedback and exact clipboard paste-back,
  plus the edited text in the Android Sharesheet preview; no text was sent.
- Exact edited text retained through language recreation, theme changes and
  navigation, followed by Generate replacing it.
- Import/export document pickers canceled safely; Reset confirmation canceled.
  No real imported vocabulary was overwritten and no reset/data-clear was performed.
- Existing generator-preference bytes unchanged after upgrade and all UI checks;
  generator code, all 449 stems/weights and all existing artwork byte-for-byte
  unchanged in the repository. No AVD was started or other device used.

The final launch remained in the foreground; Logcat showed no error/fatal lines
for the app process and no app entries in the crash buffer during this milestone.
The original System default language/theme choices were restored. The phone's
actual system language is `en-GB`; no global language/night-mode or USB-debugging
setting was changed. The app was left running on the phone. True German-system
and unsupported-system-language scenarios and the API 28–32 locale path were not
tested on hardware; deterministic JVM tests cover language resolution/fallback
and resource/format parity. No external share recipient, browser source-link
request, destructive import/reset or public release action was performed.

### Historical v1.2.0 lint cleanup (before naming cleanup)

The complete input report contained **16 errors, 28 warnings and 1 hint**. All 16
errors were `LocalContextGetResourceValueCall`. The cleanup reads strings in
composition, including text captured by semantics/click callbacks; settings option
label lambdas are composable. Structured feedback resolves through the observed
`LocalResources`, and import/count text uses independent Android plurals in English
and German. Diagnostic indices and bounds are phrased explicitly rather than as
quantities. Generation, vocabulary, appearance/language storage keys and artwork
are unchanged. Unused template XML colors and the redundant activity label were
removed; `maxWordCount` uses primitive Compose integer state.

Autofix proposals were reviewed against the uncommitted working tree. Blindly
inserting `stringResource` into non-composable callbacks would not compile, so
resource reads are made in the surrounding composition. KTX `edit(commit = true)`
returns `Unit`; replacing the legacy locale save's Boolean `commit()` would lose
failure reporting. That proposal was intentionally not applied. No lint baseline,
severity changes or lint suppressions were added; pinned dependency/tool versions
were preserved.

Local verification uses the regular declared dependencies, with no substitutions:

```sh
./gradlew --offline --no-configuration-cache testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin lintDebug assembleDebug
ANDROID_SERIAL=<discovered-Medium-Phone-serial> ./gradlew --offline --no-configuration-cache \
  -Pandroid.injected.device.serial=<discovered-Medium-Phone-serial> :app:connectedDebugAndroidTest
```

The local tasks succeed: **50 JVM tests**, successful Android-test compilation,
`lintDebug` and `assembleDebug`. The current lint report contains **0 errors,
11 warnings and 0 hints**. The full instrumentation
suite passes **14 tests, 0 failures/errors/skips** on Medium Phone (Android 16 /
API 36), with its serial resolved dynamically and supplied via both
`ANDROID_SERIAL` and `android.injected.device.serial`. It includes real activity
language/theme changes with retained edits/navigation, German About, resource
updates while the Context stays identical, and independent word/connector plurals.
The German About check uses the real activity locale rather than only overriding
composition locals outside a platform dialog. Existing Copy/Share, count,
persistence and settings tests pass as well. This cleanup does not install or
test on a physical phone. The agent started Medium Phone and shut it down cleanly
afterward; the attached Samsung was left untouched.
The pre-existing uncommitted v1.2.0 implementation and documentation were preserved.

Remaining lint warnings are reviewed and intentionally visible:

| Issue | Count | Assessment |
| --- | ---: | --- |
| `UnusedAttribute` | 1 | `localeConfig` is used on API 33+. API 28–32 uses the existing persisted configuration-context fallback; ignoring this attribute there is intentional. |
| `AndroidGradlePluginVersion` | 2 | Notices for pinned Gradle/AGP versions; upgrades are outside this cleanup. |
| `GradleDependency` | 1 | Compose BOM update notice; current version retained. |
| `NewerVersionAvailable` | 2 | Kotlin Compose plugin / Serialization update notices; current versions retained. |
| `IconXmlAndPng` | 2 | Existing full-color adaptive XML and density bitmap variants retained. Adding a v26 qualifier instead produces `ObsoleteSdkInt` with minSdk 28 and provides no compatibility benefit. |
| `MonochromeLauncherIcon` | 2 | Optional Android 13 themed-icon masks are not supplied. Existing artwork remains unchanged; a proper monochrome design is separate artwork work. |
| `UseKtx` | 1 | Synchronous legacy locale persistence needs the `commit()` Boolean to report save failure; the suggested KTX helper discards it. |

Reports: `app/build/reports/lint-results-debug.html` (also XML/text/SARIF),
`app/build/reports/tests/testDebugUnitTest/`, and
`app/build/reports/androidTests/connected/debug/`. The debug APK remains at
`app/build/outputs/apk/debug/app-debug.apk`.

### Historical naming cleanup verification

The pre-upload identity is now consistent: Gradle root project **DadaBlubb**,
package/namespace/applicationId **`com.github.jschbe.dadablubb`**, Android style
**`Theme.DadaBlubb`**, and internal Compose names **`DadaBlubbApp`** and
**`DadaBlubbTheme`**. All 25 Kotlin source/test files were moved into matching
`com/github/jschbe/dadablubb` directories. The archived artwork source is now
`app/artwork/DadaBlubb.png`; its bytes and all launcher artwork are unchanged.
Version **1.2.0 / code 3** was unchanged. At the time of the naming cleanup,
**jschbe/DadaBlubb** was the planned URL; the repository has since been renamed
and made public. Git remotes, commits/tags and the local checkout directory were
not renamed or rewritten by that cleanup.

Checks against the uncommitted starting tree confirmed that Kotlin content differs
only in reviewed identity names/imports/URLs, with no generation or other functional
changes. `dada.json` (449 stems and original weights), artwork bytes, dependency
versions, backup/locale XML rules and license texts are unchanged. Existing private
filenames and preference keys remain the same within the new app's separate
sandbox. No automatic migration or old-package access was added.

Regular offline verification, with no substitute dependency versions:

```sh
./gradlew --offline --no-configuration-cache testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin lintDebug assembleDebug
ANDROID_SERIAL=<discovered-Medium-Phone-serial> ./gradlew --offline --no-configuration-cache \
  -Pandroid.injected.device.serial=<discovered-Medium-Phone-serial> :app:connectedDebugAndroidTest
./gradlew --offline --no-configuration-cache -q properties --property name
```

| Check | Result |
| --- | --- |
| `testDebugUnitTest` | 50 passed; 0 failures/errors/skips; all test classes use the new package |
| `:app:compileDebugAndroidTestKotlin` | Successful |
| `lintDebug` | Successful; 0 errors, 11 reviewed warnings, 0 hints (same warning categories as the lint-cleanup table) |
| `assembleDebug` | Successful; APK at `app/build/outputs/apk/debug/app-debug.apk` |
| `:app:connectedDebugAndroidTest` | 14 passed; 0 failures/errors/skips on Medium Phone, Android 16 / API 36, explicitly targeted by its discovered serial |
| Gradle `name` property | `DadaBlubb` |

APK inspection confirmed the new applicationId, launcher class
`com.github.jschbe.dadablubb.MainActivity`, label **Dada Blubb**, version **1.2.0 /
3**, minSdk **28** and targetSdk **37**. The merged dependency-generated startup
authority and receiver permission use the new applicationId; the test APK targets
the new package. No old package reference remains in either merged manifest or
in app DEX package descriptors. There is no app-owned file provider/authority:
JSON import/export still uses Storage Access Framework contracts, and sharing is
plain text through the system chooser.

The final APK was installed and launched explicitly on Medium Phone after the
test deployment was cleaned up. Generate produced nonempty editable output.
Import and Export both opened the system document picker and returned to the new
app on cancellation; no document was overwritten and no reset was confirmed.
The existing suite verified edited text, clipboard/Share payloads, theme/language
selection, navigation and About including the source URL. The app stayed
alive with no fatal exception or app crash-buffer entry. Android 16 Logcat emitted
ashmem deprecation and blocked accessibility hidden-API diagnostics during UI
inspection; no failed UI operation was observed. Dependency upgrades are outside
this naming-only change.

No physical device was installed, started or tested. Neither identity was present
on Medium Phone at the beginning, so coexistence with a populated old installation
was not exercised there. No old installation was uninstalled, cleared, reset or
migrated. The connected Samsung was left untouched. The agent started Medium
Phone for this run and shut down only that emulator cleanly at completion.

A case-insensitive audit of maintained current files leaves the old identifier
only in the explicit historical applicationId/data-separation note near the top
of this README. The old local directory/remote name, Git history and ignored
historical build/cache paths are intentionally not rewritten. No current source,
resource, test, Gradle identity or artwork filename uses the former name.

Human listening, external receiving apps/providers, older-device locale handling,
large-font accessibility and release signing remain separate acceptance work.
No repository publication, release upload or Play Store action is part of this
milestone; commits, tags and pushes require an explicit request.

## License and credits

Copyright © 2026 Jochen Schmitt.

**SPDX license expression: `GPL-3.0-only`.** The author's original code, bundled
vocabulary/data and original project artwork contributions are licensed under
the **GNU General Public License version 3 only**, to the extent the author holds
applicable rights. The full, unmodified official GNU GPL v3 text is in [LICENSE](LICENSE).
This grant does not relicense third-party dependencies or materials; they retain
their own licenses, copyrights and notices. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the included Apache 2.0 text.

The Grottenolm / “di blubb” icon was created using OpenAI image generation based
on Jochen Schmitt's instructions. This license statement does not assert copyright
protection for AI-generated output beyond what applicable law grants. Original
human-authored contributions and any rights actually held are covered by the
project's license; third-party rights remain unaffected.

Developed with the assistance of OpenAI Codex.

Public source: [github.com/jschbe/DadaBlubb](https://github.com/jschbe/DadaBlubb).
The visible name is
Dada Blubb; the Gradle project is DadaBlubb and the package/applicationId is
`com.github.jschbe.dadablubb`. Git remotes and the local checkout directory are
left unchanged. See Installation identity and existing data above for the
separate-installation consequence of this pre-upload change.

## Privacy and offline behavior

No INTERNET or storage permission, backend, analytics, ads, or network calls.
Generation, validation, and private persistence work offline. Clipboard, sharing,
and document providers and the source-code link use Android system features; other
apps/providers may have their own network behavior.

### Android backup policy

Automatic **Android app-data cloud backup is excluded by configuration**; supported
device-to-device transfers remain allowed (`allowBackup=true`). This covers all
nine Android backup domains, including credential/device-protected files and
preferences. Imported `files/active-dada.json`, `files/output.txt`, generator and
UI SharedPreferences are excluded from cloud backup and eligible for D2D.

- API 28–30 uses `fullBackupContent`: every explicit include requires the
  `deviceToDeviceTransfer` transport flag. Cloud transports without that flag
  cannot include those files; transfers without the flag cannot include them either.
- API 31+ uses `dataExtractionRules`: every domain is excluded from `cloud-backup`
  and explicitly included in `device-transfer`.
- Android always excludes cache/no-backup directories. The bundled asset ships
  with the APK and does not depend on restored data.

These are Android framework policy rules, not a guarantee that any particular
OS/OEM/transport will implement D2D migration or honor every rule. Existing older
cloud snapshots are not retroactively erased. OS-owned settings and user-directed
exports, clipboard, shares or provider storage are outside these app-private
backup rules, so this is not a claim that text can never reach cloud storage.
No cloud backup, restore, real device migration or global backup setting was
triggered during these review fixes. See
[Android Auto Backup rules](https://developer.android.com/identity/data/autobackup).
