---
paths:
  - "app/src/main/java/mx/dev1/naturequest/ui/**/*.kt"
  - "app/src/main/res/values*/strings.xml"
---

# UI rules

- Design every screen to be used in a few seconds: one primary action, short text. The phone goes back in the pocket.
- Large tap targets (primary actions at least 72dp tall, nothing under 48dp) and high contrast for outdoor sunlight. Use the app theme; dynamic color stays off.
- No strings outside resources: use `stringResource(R.string.…)`. Every new string goes in both `values/strings.xml` (English) and `values-es/strings.xml` (Spanish).
- Every long operation (model load, generation, verification, download) shows progress and can be cancelled.
- ViewModels expose a single `UiState` through `StateFlow`; screens collect it with `collectAsStateWithLifecycle()`.
- In `DisposableEffect`, copy state into a local inside the effect before `onDispose`; reading the state there sees the new value and cleans up the wrong thing (it unbound a freshly bound camera).
