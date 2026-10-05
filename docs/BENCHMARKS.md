# Benchmarks: Gemma 4 E2B on-device (model spike)

Measured on 2026-10-05 with the debug-only spike screen (`SpikeActivity`), engine-reported timings
(`ExperimentalFlags.enableBenchmark`) plus wall-clock time around each request.

| | |
|---|---|
| Device | Google Pixel 10 (Tensor G5), Android 16 (API 36), 11.3 GiB RAM, USB connected |
| Runtime | LiteRT-LM `litertlm-android` 0.17.1 |
| Model | `gemma-4-E2B-it.litertlm` (generic build, 2.59 GB, SHA-256 verified), thinking disabled |
| Settings | context 2048 tokens, `maxNumImages` 1, temperature 0.8 (generation) / 0.2 (verification) |

## Targets vs results

| Target | Result | Status |
|---|---|---|
| Model load < 20 s | CPU: 3.1 s on the first CPU load, 0.4-0.8 s afterwards. GPU: **25.0 s** the first time ever, 3.0-5.0 s afterwards | Met (CPU) |
| Verification < 10 s | CPU, 140 visual tokens / 640 px: **5.7-6.5 s**; 70 tokens / 512 px: 5.4-5.9 s. At 280 tokens / 768 px: 7.3-8.2 s on a cool phone but **11.2-11.9 s** once the phone was slow | Met with <= 140 visual tokens |
| Generation < 20 s | CPU: 4.6-11.9 s (5, 8 and 12 items). GPU: 10.2-15.6 s | Met |
| Verification correct on >= 8 of 10 samples | **Not measured: `/samples` has no photos yet** | Pending |

Verification timing was measured on a drawn red "leaf" image (4000x3000, downscaled like a real
photo), never scored. It proves the image path works end to end and gives latency, not accuracy.

## Load time

| Backend | First load ever | Next process | Reload in same process |
|---|---|---|---|
| CPU | 3.1 s | 0.4-0.8 s | 0.4-0.6 s |
| GPU | 25.0 s (builds a kernel cache in `cacheDir`) | 3.0 s | 5.0 s |

Caveat: the model file had just been copied, so it was in the OS page cache. A load after a reboot also
has to read 2.59 GB from flash, which these numbers do not include.

## Hunt generation (4 cases: urban 5 items, park 8, forest 12, garden 8 in Spanish)

| Backend / phone state | Total per request | Time to first token | Decode speed |
|---|---|---|---|
| CPU, cool phone | 4.6-7.4 s (avg ~5.7 s) | 1.1-1.7 s | 17-23 tok/s |
| CPU, after ~25 min of back-to-back runs | 5.9-11.9 s (avg 8.4 s) | 1.8-2.1 s | slower |
| GPU | 10.2-15.6 s (avg 11.9 s) | 0.65-1.2 s | ~10 tok/s |

The output was valid JSON with the requested item count in 4 of 4 cases on every run, including Spanish.
Output length (62-147 tokens) dominates the time, because decoding is slow on this phone.

## Photo verification (synthetic image, latency only)

| Config | Prefill tokens | Total per request |
|---|---|---|
| CPU, 70 visual tokens, 512 px | 282 | 5.4-5.9 s |
| CPU, 140 visual tokens, 640 px (slow phone) | 349 | 5.7-6.5 s |
| CPU, 140 visual tokens, 640 px (cool phone, final build) | 349 | 4.5-5.0 s |
| CPU, 280 visual tokens, 768 px (cool phone) | 485 | 7.3-8.2 s |
| CPU, 280 visual tokens, 768 px (slow phone) | 485 | 11.2-11.9 s |
| CPU text + GPU vision encoder, 280 / 768 (slow phone) | 485 | 7.3-8.5 s |
| GPU, 280 / 768 | 497 | 7.7-9.0 s |

- With an image attached, wall time is clearly longer than the engine's time-to-first-token plus decode time
  (about 6 s longer at 280 tokens on CPU). That gap is the image encoder, and it shrinks a lot with fewer
  visual tokens or with the encoder on the GPU.
- A one-line, 12-word-limit answer gives about 40-44 output tokens when the photo does **not** match (message
  plus hint). A match needs fewer, so it is faster.
- The first prompt version made the model wrap its JSON in a markdown code fence. The tightened prompt
  (`verification_v1.txt`) returns one raw JSON line. The parser also tolerates fences.

## In the real app (photo verification, end to end)

On the Pixel 10, tapping the shutter to seeing the result took roughly **7-10 s**: capture, shrinking the
4000x3000 JPEG to 480x640 (about 30 ms), loading the model (about 0.5 s warm), and the model's answer
(CPU, 140 visual tokens). That was measured by polling the screen every 1-2 s, so treat it as approximate.
It is consistent with the 4.5-6.5 s spike numbers plus capture and load. Photo accuracy is still not measured.

## Memory (peak RSS, from `/proc/self/status`)

| Backend | Peak RSS | After releasing the engine |
|---|---|---|
| CPU, 70 / 140 / 280 visual tokens | 2.2 / 2.3 / 2.6 GB | ~240 MB (fully freed) |
| GPU | 1.6 GB (PSS up to 4.2 GB with GPU buffers) | ~1.1 GB RSS stays resident |

## Findings that change the design

1. **Use the CPU backend.** On the Pixel 10 it is about 2x faster than the GPU for generation, loads fast,
   and frees its memory. Google's published GPU numbers (about 52 tok/s on a Galaxy S26 Ultra) do not
   carry over to this phone, where the GPU decodes at about 10 tok/s.
2. **Decode speed is the bottleneck (about 7-23 tok/s), so keep model output short.** Every output token
   costs roughly 50-150 ms.
3. **The phone slows down under sustained load**, about 1.6x after 25 minutes of back-to-back runs, even
   though the OS reported thermal status 0. Players will be outdoors in the sun. The chosen settings must meet
   the targets in the slow state, which is why the default is 140 visual tokens at 640 px, not 280 at 768.
4. **Safety must be enforced in code.** The prompt asks for safe items, but generation still produced
   "a shape resembling a coiled snake", "a patch of vibrant orange fungus" and "a bird's nest hidden in a
   hollow". All three would send kids toward animals or mushrooms. The next step adds the validator.

## Not tried

- The `Google_Tensor_G5` build of the model (3.1 GB, NPU). It is Pixel 10 specific, so it would not work for
  other people installing the APK.
- E4B. E2B already meets the timing targets, and a bigger model would be slower.
- Constrained JSON decoding (`ResponseFormat.json`). Prompting alone gave valid JSON in every run so far.
- A cold-from-flash load after a reboot.

## Reproduce

```bash
./gradlew installDebug
# push the model first (see CLAUDE.md), then:
adb shell am start -n mx.dev1.naturequest/.debug.SpikeActivity --ez auto true --es backend CPU --ei maxSide 640 --ei budget 140
adb logcat -s NQ_SPIKE
adb shell "run-as mx.dev1.naturequest cat files/spike_report_cpu-vcpu_b140_s640.json"
```

Put real photos in `samples/` (see `samples/README.md`) and rebuild to also score verification accuracy.
