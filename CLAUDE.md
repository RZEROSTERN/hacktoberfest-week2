# Nature Quest

Offline Android nature scavenger hunt for families and kids (DEV "Touch Grass" challenge entry). Gemma 4 E2B runs on the phone via LiteRT-LM to generate the hunt list and verify photos; no network at runtime. Kotlin, Compose, MVVM, Clean Architecture (`data/` `domain/` `ui/`), Hilt, CameraX, TextToSpeech.

## Layout
- `app/` Android app, package `mx.dev1.naturequest`
- `samples/` test photos for the model spike
- `docs/` `DECISIONS.md` (technical decisions), `BENCHMARKS.md` (spike numbers)
- `.claude/rules/` path-scoped rules: `inference.md`, `ui.md`

## Commands
Needs JDK 17+ (see Gotchas).
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Required before every commit: `./gradlew assembleDebug lintDebug testDebugUnitTest`
- Install on device: `./gradlew installDebug`
- Push the dev model (debug build installed; lands in app-private `files/`):
  `adb push <path>/gemma-4-E2B-it.litertlm /data/local/tmp/ && adb shell "run-as mx.dev1.naturequest sh -c 'mkdir -p files && cp /data/local/tmp/gemma-4-E2B-it.litertlm files/'" && adb shell rm /data/local/tmp/gemma-4-E2B-it.litertlm`

## Git: GitFlow, non-stacked PRs
- Production branch is `master`. NEVER create, push, reference or target a branch named `main`.
- Never commit directly to `master` or `develop`. `develop` changes only via `feature/*` PRs; `master` only via `release/*` or `hotfix/*` PRs.
- One `feature/<name>` per build step, cut from the latest `develop` (`git checkout develop && git pull`). Never branch from another feature branch.
- One PR per branch: `gh pr create --base develop`, then `gh pr merge --merge --delete-branch`. Start the next step only after the merge.
- PR title is a Conventional Commit; body says what changed, why, how tested, and whether `docs/DECISIONS.md` changed.

## Workflow
- Lint + unit tests + build must pass before each commit. Small Conventional Commits.
- Log every technical decision (made or changed) in `docs/DECISIONS.md`.
- Check the official docs instead of guessing LiteRT-LM, CameraX, TextToSpeech or Hugging Face APIs; say so when something is undocumented.
- Unit tests for ViewModels, JSON parsing and safety validation, using a fake `InferenceEngine`.
- When corrected on something that should outlive the session, propose adding it here or to `.claude/rules/`.

## Non-negotiables
- No closed-model APIs, no server inference. The only network call is the one-time model download.
- Photos never leave the device: in memory for verification, app-private cache only during a hunt, deleted when it ends unless the user taps "Save to gallery". No analytics or telemetry.
- Generated content is safe: photograph, don't pick (never ask to pick plants or flowers, or to collect, touch or approach animals, insects or mushrooms); nothing that needs leaving paths, climbing, or going near water or roads; never suggest eating or tasting anything; encourage kids to hunt with an adult.
- All UI strings live in resources: `values/` (English, default) and `values-es/` (Spanish). Model prompts and responses follow the device language.

## Gotchas
- Shell `JAVA_HOME` may point at JDK 11, and Gradle then fails with "requires JVM 17". Export a JDK 17+ first.
- `compileSdk`/`targetSdk` are 37 because current AndroidX requires it; the platform must be installed (`sdkmanager "platforms;android-37.0"`).
- AGP 9 has built-in Kotlin: do not apply `org.jetbrains.kotlin.android`. Use KSP, not kapt.
- Lint `HardcodedText` only checks XML. Strings hardcoded in Compose pass lint, so review for them.
- Dev model location is app-private `files/`, the same place the release download goes. `run-as` only works on debug builds.
- `.litertlm` files, keystores and `local.properties` are gitignored. Never commit them.
