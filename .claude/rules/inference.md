---
paths:
  - "app/src/main/java/mx/dev1/naturequest/data/**/*.kt"
  - "app/src/main/java/mx/dev1/naturequest/domain/**/*.kt"
  - "app/src/main/assets/prompts/**"
---

# Inference and data rules

- The engine sits behind a domain interface (`InferenceEngine`). Only its data-layer implementation may import LiteRT-LM types; ViewModels and use cases depend on the interface, and unit tests use a fake.
- Generation and verification responses are strict JSON, parsed with kotlinx.serialization. On invalid output retry once, then use a safe fallback: verification returns "I'm not sure, try another photo"; generation returns a bundled default hunt (EN/ES string resources).
- Prompts live in versioned files under `app/src/main/assets/prompts/` (for example `hunt_generation_v1.txt`), never inline in Kotlin. Bump the version when a prompt changes and note why in `docs/DECISIONS.md`. Prompts and responses follow the device language.
- Safety is enforced twice: in the prompt and in code. Validate every generated item and reject and regenerate any that breaks the rules in `CLAUDE.md`.
- Model loading and inference always run off the main thread (inject the dispatcher so tests can replace it). Engine initialization is blocking.
- Cancelling a coroutine must also stop native inference: call `conversation.cancelProcess()` (the Flow does not).
- Decode is slow on the target phone (about 7-23 tok/s), so keep each prompt's answer short and cap `maxOutputTokens`. Defaults: CPU backend, 140 visual tokens, photos downscaled to 640 px. Numbers are in `docs/BENCHMARKS.md`.
- Load the engine per use and release it right after (warm reload is 0.4-0.8 s; loaded it holds 2+ GB). Pass a random `seed` when variety matters: the engine default is deterministic.
- Release the engine when it is not needed (hunt over, app backgrounded, ViewModel cleared). It holds gigabytes of memory.
- Photos are handled in memory and are never uploaded. This layer makes no network calls except the model download.
- Check the official LiteRT-LM docs before using its API. Do not guess.
- The language of a prompt or a voice comes from `AppLanguage.resolve(locale)`, never from the raw device locale: the safety word lists only cover English and Spanish.
