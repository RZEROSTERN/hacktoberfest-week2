# Test photos

Photos used by the debug-only model spike to check photo verification. They are bundled into
**debug builds only** and never into release builds.

Name each file after what it shows, optionally followed by a number:

- `leaf1.jpg`, `leaf2.jpg` -> label `leaf`
- `red_leaf_3.jpg` -> label `red leaf`
- `bark-with-moss.jpg` -> label `bark with moss`

For every photo the spike asks the model whether it shows its own label (expected: match) and
whether it shows a different label from the set (expected: no match). Supported formats: JPG, PNG,
WebP. Use real, un-edited phone photos: that is what players will send.
