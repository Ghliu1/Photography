# PhotoGuide 📸

A real-time camera **coaching overlay** for Android. PhotoGuide watches the
live viewfinder and tells you how to move the phone for a better shot —
improving **composition**, **angle/level**, and **lighting** of your subject.

It is a fully on-device app (no network, no cloud): face detection runs with
ML Kit, lighting is measured straight off the camera's luma plane, and the
"is the phone level?" check uses the gravity sensor.

## What it does

While you frame a shot, a translucent overlay shows:

| Feature | How it helps |
|---|---|
| **Rule-of-thirds grid** | A classic composition guide drawn over the preview. |
| **Spirit level** | A bubble line driven by the gravity sensor turns red when the phone is tilted, green when level. |
| **Subject box** | Tracks the largest face; turns amber when framing/composition needs work. |
| **Guidance arrow** | A big arrow shows exactly which way to move/rotate the phone. |
| **Coaching banner** | One plain-language tip at a time (the most urgent one). |

### The coaching rules

The brain of the app is [`GuidanceEngine`](app/src/main/java/com/photoguide/app/analysis/GuidanceEngine.kt),
a pure-Kotlin class that turns a frame snapshot into prioritized tips:

- **Framing** — "Subject is cut off — step back", "Move closer", "Step back".
- **Composition** — headroom ("tilt down/up a little") and edge avoidance
  ("hugging the left edge — pan left to recenter"), nudging the face toward
  the upper-third line.
- **Lighting** — "Too dark", "Too bright", and backlight detection
  ("Subject is backlit — turn so the light hits their face") by comparing the
  brightness of the frame's center against its surroundings.
- **Level** — "Straighten up — keep the horizon level" with a rotate arrow,
  plus a warning when the phone is pitched far forward/back.

Tips are ranked by severity, and only the single most important one is shown
so the guidance never gets noisy. When everything checks out you get
**"Looks great — hold steady and shoot!"**

## Architecture

```
CameraActivity            CameraX preview + ImageCapture + ImageAnalysis, sensors, UI
 ├─ FrameAnalyzer         per-frame: ML Kit face detection + luminance + tilt → FrameSignals
 │   ├─ LuminanceAnalyzer center vs surround brightness from the YUV luma plane
 │   └─ TiltSensor        smoothed roll/pitch from the gravity sensor
 ├─ GuidanceEngine        pure Kotlin: FrameSignals → ranked Tips   (unit-tested)
 └─ OverlayView           draws grid, level, subject box and guidance arrow
```

`FrameSignals`, `Guidance`/`Tip`, and `GuidanceEngine` contain **no Android
types**, so the photography heuristics are unit-tested on the JVM — see
[`GuidanceEngineTest`](app/src/test/java/com/photoguide/app/analysis/GuidanceEngineTest.kt).

## Build & run

Requires Android Studio (or the Android SDK + the Gradle wrapper) with an SDK
that has API 34 installed.

```bash
# Create local.properties pointing at your SDK (Android Studio does this for you):
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug          # build the APK
./gradlew installDebug           # install on a connected device
./gradlew test                   # run the GuidanceEngine unit tests
```

Then launch **PhotoGuide**, grant camera permission, and point it at a person.
Use the shutter button to save a shot (to `Pictures/PhotoGuide`) and the
mini button to flip between the front and back cameras.

- `minSdk` 24, `targetSdk`/`compileSdk` 34, Kotlin, view binding.
- Key libraries: CameraX 1.3, ML Kit Face Detection (bundled model).
