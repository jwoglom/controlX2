# Patches

This directory contains patches for third-party dependencies.

## Vico NaN Fix (`vico-core-nan-fix.patch`)

### Problem
The Vico charting library (v2.3.6) crashes when NaN (Not a Number) values are present in the chart data. The error occurs in `LineCartesianLayer.kt` at line 471:

```
java.lang.IllegalArgumentException: Cannot round NaN value.
at kotlin.math.MathKt__MathJVMKt.roundToInt(MathJVM.kt:1192)
at com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer.updateMarkerTargets(LineCartesianLayer.kt:471)
```

### Solution
The patch adds NaN validation checks in the `updateMarkerTargets` function before calling `roundToInt()` on float values. This prevents the crash by returning early when NaN values are encountered.

### Changes
```kotlin
protected open fun CartesianDrawingContext.updateMarkerTargets(
  entry: LineCartesianLayerModel.Entry,
  canvasX: Float,
  canvasY: Float,
  lineFillBitmap: Bitmap,
) {
  if (canvasX <= layerBounds.left - 1 || canvasX >= layerBounds.right + 1) return
  // Guard against NaN values which would cause roundToInt() to throw IllegalArgumentException
  if (canvasX.isNaN() || canvasY.isNaN()) return  // <-- ADDED
  val limitedCanvasY = canvasY.coerceIn(layerBounds.top, layerBounds.bottom)
  // ... rest of function
}
```

### Application
The patch is automatically applied during the build process via `mobile/vico-patch.gradle`. The build script:
1. Downloads the Vico source JAR
2. Extracts `LineCartesianLayer.kt`
3. Applies the NaN fix
4. Compiles the patched source
5. Packages it as a JAR that takes precedence over the original dependency

### Upstream Submission
This patch should be submitted to the Vico project:
- Repository: https://github.com/patrykandpatrick/vico
- File: `vico/core/src/main/java/com/patrykandpatrick/vico/core/cartesian/layer/LineCartesianLayer.kt`
- Affected versions: 2.3.6 (and likely earlier)

### Testing
To verify the patch is applied:
```bash
./gradlew :mobile:createPatchedVicoJar
```

To test the full build:
```bash
./gradlew build
```
