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

