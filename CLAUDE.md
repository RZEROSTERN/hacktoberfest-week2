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
- Benchmark on the phone (debug only, results in `docs/BENCHMARKS.md`): `adb shell am start -n mx.dev1.naturequest/.debug.SpikeActivity --ez auto true --es backend CPU --ei maxSide 640 --ei budget 140`, then `adb logcat -s NQ_SPIKE`

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
- LiteRT-LM: its `main` branch is ahead of the pinned 0.17.1 AAR, so check APIs with `javap` on the AAR. `Message` has no `.text`, use `toString()`. Cancelling the `sendMessageAsync` Flow does not stop native inference: call `conversation.cancelProcess()`.
- LiteRT-LM `SamplerConfig.seed` defaults to 0, so sampling is deterministic: pass a random seed when variety matters (hunt generation does).
- CameraX in-memory capture is JPEG and already carries EXIF orientation: rotate only when EXIF has none, or the photo ends up sideways.
- Device UI tests: `adb shell pm grant mx.dev1.naturequest android.permission.CAMERA`; the debug `MainActivity` shows over the lock screen. Photos of the room can end up on screen, so check logs, not photos.
- Android regex (ICU) throws on a bare `}` that the JVM accepts. Escape both braces; JVM unit tests will not catch it.
- On the Pixel 10 the CPU backend beats GPU, decode is only ~7-23 tok/s, and the phone slows ~1.6x under sustained load. Keep model output short and benchmark in the slow state.
- `.litertlm` files, keystores and `local.properties` are gitignored. Never commit them.
