# Decisions log

Technical decisions for Nature Quest, newest at the bottom. Each entry: what was decided, why, and what was rejected.
Update this file whenever a decision is made or changed (it feeds the write-up).

## D-001 GitFlow with `master` as production (2026-10-05)
- **Decision:** `master` (production), `develop` (integration), `feature/*` (one per build step), `release/*`, `hotfix/*`. A branch named `main` is never used.
- **Why:** challenge rule set by the author; one PR per step keeps history reviewable.
- **Rules:** PRs are never stacked: every feature branch is cut from the latest `develop` and its PR targets `develop`.
- **Bootstrap exception:** GitHub repo was empty, so one initial commit (`README.md`) went straight to `master` to create the branch. `develop` was cut from it. No other direct commits.

## D-002 Toolchain: AGP 9.4.1, Gradle 9.6.0, Kotlin 2.4.20, KSP (2026-10-05)
- **Decision:** AGP 9.4.1 with built-in Kotlin (no `org.jetbrains.kotlin.android` plugin), Gradle 9.6.0 (AGP 9.4 minimum), Kotlin 2.4.20, KSP 2.3.12 for Hilt (no kapt), JDK 17, version catalog in `gradle/libs.versions.toml`.
- **Why:** current stable versions at build time (checked on Maven metadata, not from memory). AGP 9 removes the need for the Kotlin Android plugin; kapt is being phased out.
- **Rejected:** older AGP 8.x (extra setup debt for no benefit).

## D-003 `compileSdk`/`targetSdk` 37, `minSdk` 31 (2026-10-05)
- **Decision:** `compileSdk = 37`, `targetSdk = 37`, `minSdk = 31` (Android 12).
- **Why:** the current AndroidX releases (Compose 1.12.1, core-ktx 1.19.1, lifecycle 2.11.0, navigation 2.10.2) require `compileSdk` 37, which meant installing SDK platform 37 on the dev machine. Pinning older AndroidX was rejected: it would recur with every new dependency (CameraX, LiteRT-LM). `minSdk 31` because on-device Gemma 4 needs a recent, capable phone anyway and it lets us use a single adaptive-icon/`dataExtractionRules` code path.
- **Resolved (model spike):** the LiteRT-LM docs do not state a minimum Android version, but the 0.17.1 AAR merged and ran on `minSdk 31` with no changes.

## D-004 UI stack: Compose + Material 3, Navigation Compose with type-safe routes (2026-10-05)
- **Decision:** Compose BOM 2026.09.00, Material 3, Navigation Compose 2.10.2 with `@Serializable` route objects, Hilt via `hilt-navigation-compose`.
- **Why:** simplest well-documented combo; the app has about four screens. Navigation 3 exists but adds learning cost with no payoff here.
- **Theme:** dynamic color is off and the palette is fixed and high-contrast so the UI stays legible in direct sunlight; type is scaled up slightly because the phone is glanced at, not read.

## D-005 Privacy defaults in the manifest (2026-10-05)
- **Decision:** `allowBackup="false"` plus `dataExtractionRules` that exclude everything from cloud backup and device transfer. No `INTERNET` permission until the model-download step, which is the only network use.
- **Why:** kids' photos and hunt history must never leave the phone, including through OS backup. Lint flagged that `allowBackup` alone is ignored by Android 12+ device transfer.

## D-006 Strict lint, strings only in resources (2026-10-05)
- **Decision:** lint aborts the build on any issue (`warningsAsErrors`); `MissingTranslation`, `ExtraTranslation` and `HardcodedText` are errors. Version-bump nags are disabled (versions live in the catalog).
- **Why:** English default (`values/`) and Spanish (`values-es/`) must stay in sync. Note that `HardcodedText` only covers XML, so hardcoded strings inside Compose code are caught by review and `.claude/rules/ui.md`, not lint.

## D-007 Release build is not minified (2026-10-05)
- **Decision:** `isMinifyEnabled = false` for now.
- **Why:** LiteRT-LM uses JNI and reflection; shrinking risks runtime crashes that would cost time we do not have. Revisit only if APK size becomes a problem.

## D-008 Dev and release share one model location: app-private `files/` (2026-10-05)
- **Decision:** the app looks for the `.litertlm` model in its own `files/` directory. In development it gets there with `adb push` to `/data/local/tmp/`, then `adb shell run-as … cp` into `files/` (debug builds only). The release download writes to the same directory.
- **Why:** verified on the target phone. `run-as` could not read `/sdcard/Android/data/<pkg>/files/` after an `adb push` there (permission denied), so that route was rejected. Using one location means the dev path exercises the same code as the release path, and the model never sits in shared storage.
- **Cost:** the model is briefly on disk twice during the push (once in `/data/local/tmp`, once in `files/`).

## D-009 Claude Code project memory layout (2026-10-05)
- **Decision:** a short `CLAUDE.md` (project, commands, GitFlow, non-negotiables, gotchas), two path-scoped rules (`.claude/rules/inference.md` for `data/`, `domain/` and prompts; `.claude/rules/ui.md` for `ui/` and `strings.xml`), and a gitignored `CLAUDE.local.md` for machine-specific details (device, JDK path, model path).
- **Why:** the official docs say instructions get followed more reliably when they are short and specific, and that anything relevant to only part of the codebase belongs in `paths:`-scoped rules, which load only when Claude touches matching files. Path scoping uses the `paths` frontmatter key (a YAML list of globs), the only field Claude Code reads from a rule.

## D-010 Inference stack: LiteRT-LM 0.17.1 + Gemma 4 E2B behind `InferenceEngine` (2026-10-05)
- **Decision:** `com.google.ai.edge.litertlm:litertlm-android:0.17.1` (Google Maven), pinned, not `latest.release`. Model `gemma-4-E2B-it.litertlm` (generic build, 2.59 GB) from `litert-community/gemma-4-E2B-it-litert-lm`. Only `LiteRtInferenceEngine` imports LiteRT-LM; everything else uses the `InferenceEngine` interface.
- **Why:** the repo is public, **not gated** (no token needed), Apache-2.0, so the app can download it with no embedded credentials. SHA-256 of the download matched the value Hugging Face publishes.
- **Gotchas found:** the library's `main` branch is ahead of the published AAR (`BenchmarkInfo.markDurationsInSecond` does not exist in 0.17.1), so APIs are checked against the pinned AAR with `javap`. `Message` has no `.text`, use `toString()`. The `sendMessageAsync` Flow ends with `awaitClose {}`, so cancelling a collector does not stop native inference: the engine calls `conversation.cancelProcess()` itself (verified on device: cancel mid-generation, no crash, memory freed, engine reusable).

## D-011 CPU backend by default (2026-10-05)
- **Decision:** `Backend.CPU` for the text model and the image encoder.
- **Why:** measured on the Pixel 10 (see `docs/BENCHMARKS.md`): generation about 2x faster than GPU (5.7 s vs 11.9 s average), model load 3 s once and 0.5 s after, memory fully freed on release. The GPU's first-ever load took 25 s and its decode ran at about 10 tok/s.
- **Rejected:** GPU (slower here, 25 s first load, memory stays resident after release); the `Google_Tensor_G5` model build (3.1 GB, Pixel 10 only, would not work for other people installing the APK).

## D-012 Photo verification defaults: 140 visual tokens, 640 px, short answers (2026-10-05)
- **Decision:** downscale photos in memory to at most 640 px (JPEG, EXIF rotation applied) and give the model a 140 visual token budget. The verification prompt asks for one line of raw JSON with 12-word messages.
- **Why:** decode runs at only about 7-23 tok/s and the phone slows about 1.6x under sustained load, so 280 tokens / 768 px missed the 10 s target in the slow state (11.2-11.9 s) while 140 / 640 hit 5.7-6.5 s.
- **Provisional:** accuracy at this budget is **not measured** because `/samples` has no photos yet. Re-run the spike on real photos and move to 280 / 768 only if 140 loses accuracy.

## D-013 arm64-only APK (2026-10-05)
- **Decision:** `abiFilters += "arm64-v8a"` and the lint check `ChromeOsAbiSupport` disabled.
- **Why:** LiteRT-LM ships arm64-v8a and x86_64 native libraries; the model needs a real phone, so x86_64 only adds about 26 MB.

## D-014 Prompts and generation parameters (2026-10-05)
- **Decision:** prompts are versioned files in `assets/prompts/` (`hunt_generation_v1`, `verification_v1`) filled by a tiny `{{name}}` template; thinking is disabled; temperature 0.8 for generation and 0.2 for verification.
- **Why:** thinking tokens cost seconds we do not have. `verification_v1` was tuned during the spike (before its first release): the first draft made the model wrap its JSON in a code fence and write long messages, adding about 20 output tokens. The JSON parser still tolerates fences and surrounding prose.
- **Finding:** on Android the template regex needs both braces escaped (`\{\{(\w+)\}\}`); the JVM accepts a bare `}` but Android's ICU regex throws, so the JVM unit tests passed while the app crashed.

## D-015 Debug-only spike tooling (2026-10-05)
- **Decision:** `SpikeActivity` lives in `src/debug/` (never in release). `/samples` is added as debug assets only. The run is started and read through adb (`--ez auto true`, logcat tag `NQ_SPIKE`, JSON report in app-private `files/`), and the activity shows over the lock screen and keeps the screen on.
- **Why:** repeatable benchmarks without tapping the phone, and no test photos ever ship in a release APK.


## D-016 Safety is a deterministic gate in code, not just a prompt (2026-10-05)
- **Decision:** every generated item passes `SafetyValidator` before a child sees it. It matches whole words (English and Spanish, accents and case ignored) against lists of: touching/taking/approaching/eating verbs; water, heights, roads and vehicles; animals, insects, nests, mushrooms and hiding places; fruit, berries and hazards; and people. Unsafe, repeated, empty, over-long or odd items are rejected and the model is asked again for only what is missing, told what to avoid (up to 3 attempts, 1 retry after invalid JSON, 2 spare items requested). If it still falls short, the built-in safe list (string resources, EN and ES) fills the gap, so a hunt always has the requested number of items.
- **Why:** the spike showed the prompt alone is not enough: the model produced "a coiled snake", "orange fungus" and "a bird's nest hidden in a hollow" even though the prompt forbade them.
- **Trade-offs:** it over-blocks on purpose (for example "a street sign" and "something under the sky"); a rejected item is just regenerated. Birds, butterflies and feathers are allowed because they can be photographed from a distance. Photos of people are blocked as a privacy rule for children. Word lists are a safety net, not a replacement for the adult supervision the app asks for on the first screen.
- **Guard tests:** every built-in fallback item (EN and ES, read straight from the XML) must pass the validator, and the real prompt files must render with the variables the code sends.

## D-017 Load the model for each use and release it right after (2026-10-05)
- **Decision:** `GenerateHuntUseCase` loads the engine, generates, and releases it in a `finally`. Photo verification will do the same.
- **Why:** the model holds 2.2-2.6 GB while loaded and the next use is minutes away, while a warm reload costs only 0.4-0.8 s (see `docs/BENCHMARKS.md`). Verified on the phone: process memory goes from 1.8 GB to 247 MB when a generation is cancelled.

## D-018 Generation parameters and prompt v2 (2026-10-05)
- **Decision:** `hunt_generation_v2` replaces v1 (the `avoid` list and the spare items needed a new prompt variable). Temperature 0.8, a **fresh random seed per request**, and Spanish plus English examples with the instruction to write natural phrases.
- **Why:** on the phone the same settings returned the identical hunt three times in a row: LiteRT-LM's `SamplerConfig.seed` defaults to 0, which makes sampling deterministic. And with English-only examples the Spanish items were abstract labels ("Estaca de piedra lisa"); with bilingual examples they became natural phrases ("Un tronco con grietas marcadas"). Verification keeps seed 0 and temperature 0.2 on purpose, so the same photo gets the same verdict.

## D-019 Hunt settings travel in a type-safe route; ViewModel built with assisted injection (2026-10-05)
- **Decision:** `Hunt(place, length, ageRange)` is a `@Serializable` navigation route and `HuntViewModel` receives `HuntSettings` through Hilt assisted injection, not a `SavedStateHandle`.
- **Why:** the ViewModel stays a plain constructor in unit tests (no Android `Bundle`), and the route arguments survive process death, so the hunt is simply generated again. The route enums carry `@Keep` so their serializers survive minified builds (lint requires it).

## D-020 Verification: the player picks the item, then takes the photo (2026-10-05)
- **Decision:** tapping an item on the list opens the camera for that item, and the model answers one focused question ("does this photo show X?"). The model is not asked to work out which of up to 12 items a photo matches.
- **Why:** it is the question the spike benchmarked (about 5 s), the prompt stays short, and the answer is more reliable than picking from a list. It also costs the player only one extra tap.
- **Rejected:** "just take a photo and the AI finds the matching item" (longer prompt, slower, easier to get wrong). It can be added later behind the same use case.

## D-021 Photo pipeline and privacy (2026-10-05)
- **Decision:** CameraX captures in memory, `ImageDownscaler` shrinks the photo to 640 px (JPEG, EXIF-aware), and only that small JPEG goes to the model. On a match the small JPEG, never the original, is saved to app-private cache for the summary screen. The cache is deleted when the player leaves the hunt, and when the app starts (a hunt only lives in memory, so leftovers belong to a hunt that no longer exists). The only permission added is `CAMERA`; there is still no `INTERNET` permission.
- **Verified on the Pixel 10:** CameraX's in-memory capture is JPEG (not YUV as a docs summary claimed), 4000x3000 with `rotationDegrees=90`, **and the JPEG already carries EXIF orientation**, so rotating it again would turn it sideways. The preprocessor therefore rotates only when EXIF has no orientation, and the logs confirmed a correct 480x640 portrait result.
- **Not done:** the app does not save photos to the gallery yet (the summary step adds "Save to gallery").

## D-022 The model's feedback is untrusted text (2026-10-05)
- **Decision:** the message and hint the model writes are checked by `SafetyValidator.containsUnsafeInstruction` (a looser sentence-level list: pick, touch, climb, eat, water, roads, berries and mushrooms, in English and Spanish; it still allows "I see a bee" and "take another photo"). An unsafe message is replaced by a generic localized line and the hint is dropped. Invalid JSON gets one retry with a different seed (same seed would repeat the same answer), then "I'm not sure, try another photo". A hint is only kept when the photo did not match.
- **Why:** this text is shown and later read aloud to children, and a model (or text visible in a photo) could make it say something harmful. The built-in fallback texts are covered by a guard test.

## D-023 Portrait only (2026-10-05)
- **Decision:** `MainActivity` is locked to portrait, and the matching lint checks are suppressed in the manifest.
- **Why:** one-handed outdoor use, and it keeps camera rotation simple. Android 16 ignores the lock only on large screens, which are not the target.

## D-024 The hunt in progress is an in-memory singleton (2026-10-05)
- **Decision:** `HuntSession` holds the items and found flags in memory, shared by the hunt, camera and (later) summary screens. Leaving the hunt screen ends it and deletes the photos. If Android kills the process, the hunt is lost.
- **Why:** a hunt is a short outing; persisting it would add a database for little gain. History (step 8) is a separate, optional feature.

## D-025 Hunt lifecycle: finish, leave, and who cleans up (2026-10-05)
- **Decision:** a hunt ends two ways. **Finish** (the button, or finding every item) stops the clock and opens the summary; the hunt and its cached photos stay until the summary closes, and closing it forgets the hunt, deletes the photo cache and frees the voice. **Leave** (system Back, after a confirmation dialog) forgets the hunt and deletes the photos immediately. The hunt ViewModel therefore cleans up only when the hunt was not finished; otherwise it would wipe the data the summary is about to show, and cut off the summary speech.
- **Why:** it satisfies "photos are kept only for the summary and deleted when the hunt ends" in both paths, and a stray back swipe by a child asks before throwing the hunt away.
- **Not covered on a device:** "found every item, so go straight to the summary" (it needs a real match). It is a single `LaunchedEffect`; the state it reads (`allFound`) is unit tested.

## D-026 Medal rule (2026-10-05)
- **Decision:** gold for finding everything, silver for at least half, bronze for the rest. Going outside always earns at least bronze, even with nothing found; an empty hunt is bronze, never gold.
- **Why:** this is a family outing, not an exam. A child who finished a hunt should leave with something, and "everything found" is the only way to a gold.

## D-027 Reading aloud with the phone's own speech engine (2026-10-05)
- **Decision:** Android `TextToSpeech` behind a `Speaker` interface, started on first use and released when the hunt flow ends. It speaks in the device language (falling back from es-MX to es), prefers a voice that works offline, and is silent, with everything still on screen, if the phone has no voice for the language. The manifest declares `<queries>` for `TTS_SERVICE` (Android 11+ package visibility). All spoken text comes from string resources through `HuntTexts`, so it follows the device language.
- **What is read:** the hunt list and an invitation to put the phone away "always with an adult" when the list is ready (with Read again / Stop), the model's feedback and hint after each photo, and a summary at the end.
- **Verified on the Pixel 10:** the engine started speaking in Spanish when the list appeared, Stop silenced it, and the summary was spoken. The synthesized audio itself was not heard by me; I checked the engine's own start events in the log.
- **Caveat:** the voice is a system component. If a phone has no offline voice installed for its language, there is no speech (the app does not try the network).

## D-028 "Save to gallery" is explicit and saves the small photos (2026-10-05)
- **Decision:** photos leave the app's private cache only when the player taps "Save to gallery" on the summary. They go to `Pictures/Nature Quest` through MediaStore (no storage permission needed on Android 10+). What is saved is the 640 px version used for verification, not a full-resolution original, because the original is never kept.
- **Verified on the Pixel 10** with a drawn test image through the real saver (a debug check in the spike screen); the test file and its gallery entry were then deleted.

## D-029 First-launch model download: plain HTTP, resumable, checksummed (2026-10-05)
- **Decision:** `ModelDownloader` uses `HttpURLConnection` (no new dependency). It downloads the pinned model into `files/<name>.part`, continues from the file's current size with an HTTP `Range` request, retries dropped connections (2, 5, 10, 20 and 30 s), and when all bytes are there checks the **SHA-256** and size; only then does it rename the file to its final name. A bad file is deleted, so a half-downloaded or corrupt file can never be mistaken for the model. Redirects are followed by hand (Hugging Face redirects to a CDN) and the `Range` header is re-sent on every hop; only https is accepted.
- **What is pinned:** `ModelSpec` holds the URL of a **specific repo revision** (not `main`), the exact size (2,588,147,712 bytes) and the SHA-256, so the check stays true if the repo is updated. If the model ever changes, update the URL, size and hash together. The repo is public and ungated, so no token exists anywhere in the app.
- **UX:** a download screen with progress, "X of Y (Z%)", a separate "checking the file" phase, an explicit "Wi-Fi is recommended" line, a red warning on a metered connection (such as mobile data), Cancel, and "Continue download" when a partial file exists. The screen keeps the display on. Setup, and the hunt and photo screens when the model is missing, offer the download instead of failing. Errors are specific: no space (measured with `StorageManager.getAllocatableBytes`, which counts clearable cache), connection lost (partial kept), server error, damaged file.
- **Privacy:** `INTERNET` is now in the manifest and `ModelDownloader`/`HttpDownloadSource` are the only code that opens a connection. Nothing the player does (photos, hunts) is ever sent anywhere.
- **Rejected:** WorkManager or a foreground service (more moving parts and permissions; the download happens once, on first launch, with the app open), and `DownloadManager` (little control over resume and checksum). **Trade-off:** if the app is sent to the background the system may kill it; the next start simply continues from the `.part` file. Leaving the download screen stops the download and keeps what was downloaded.
- **Tested:** 22 JVM tests including a real local HTTP server that redirects, honors `Range` and cuts the connection mid-transfer, plus a fake source for corrupt files, missing space, wrong sizes and cancellation. Run on the Pixel 10 with the real model: see the next paragraph.
- **Verified on the Pixel 10 with the real 2.59 GB model, over unmetered Wi-Fi:** with the pushed model moved aside, Setup offered the download and the screen showed "2.59 GB" and the Wi-Fi recommendation. I killed the app at 17% (485 MB on disk). On restart the screen offered "Continue download", resumed, and finished at exactly 2,588,147,712 bytes with no `.part` file; the SHA-256 check took about 5 s and passed (a wrong resume would have failed it). The finished file is byte-identical to the adb-pushed copy, and a hunt then generated with it. Speed was about 20 MB/s, roughly 2 minutes in total.
- **Not tested on the device:** a metered connection (the warning is a plain `Text`, covered by the state it reads), no free space, and a corrupt download (both covered by JVM tests with a fake source).
