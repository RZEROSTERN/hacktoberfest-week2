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
- **Open:** LiteRT-LM docs do not state a minimum Android version. Re-check against the AAR manifest in the model-spike step and adjust.

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
