# Rendering Fixes: SwiftUI-to-Compose Component Translation

## Overview

This document captures all findings and fixes made to the Android Compose rendering engine to correctly render SwiftUI-like declarative component trees (JSON payloads). The reference implementation is iOS/SwiftUI, and the goal is pixel-parity on Android using Jetpack Compose.

The test payload was the **GreenlineSR50** product page — a marketing page with a dark header, lawn mower background image, logo overlay, highlighted text, and a semi-transparent card overlay.

---

## Key Architectural Insight

The rendering pipeline works as follows:
1. A JSON payload describes a SwiftUI-like component tree (VStack, HStack, ZStack, Text, Image, Group, etc.)
2. Gson with `RuntimeTypeAdapterFactory` deserializes the JSON into Kotlin component model classes
3. `ComponentView` traverses the tree and renders each component as Jetpack Compose UI
4. Modifiers accumulate as the tree is traversed and are applied via `buildModifier()` / `buildModifierFromSubset()`

The fundamental challenge: **SwiftUI and Compose have different layout defaults**. SwiftUI defaults to top-leading alignment and intrinsic sizing, while Compose defaults to center alignment in many containers and has different sizing semantics.

---

## Fixes Applied (in chronological order)

### 1. Font Size Mapping (StringExt.kt)

**Problem**: SwiftUI font style names (`.title`, `.headline`, `.body`, etc.) mapped to incorrect Material3 text sizes, causing text to appear too large or too small.

**Fix**: Updated `toFontSize()` mapping to use closer Material3 equivalents:
- `.largeTitle` → 34sp
- `.title` → 28sp
- `.title2` → 22sp
- `.title3` → 20sp
- `.headline` → 17sp (semibold)
- `.body` → 17sp
- `.callout` → 16sp
- `.subheadline` → 15sp
- `.footnote` → 13sp
- `.caption` → 12sp
- `.caption2` → 11sp

**File**: `base-js/.../model/ext/StringExt.kt`

---

### 2. RowView Weight Removal (RowView.kt)

**Problem**: RowView (HStack) was automatically assigning `weight(1f)` to all children, causing them to equally divide space. In SwiftUI, HStack children use intrinsic sizing by default.

**Fix**: Removed the automatic `weight(1f)` assignment from Row children.

**File**: `base-js/.../composables/RowView.kt`

---

### 3. NaN Handling in JSON Deserialization (JsModule.kt)

**Problem**: The JSON payload contained `"NaN"` string values for Float fields (e.g., `offset(y: "NaN")`). Gson's default Float adapter would crash on these.

**Fix**: Added a custom `TypeAdapter<Float>` in `JsModule` that converts `"NaN"` strings to `Float.NaN` instead of crashing.

**File**: `base-js/.../di/JsModule.kt`

---

### 4. Missing Modifier Registrations (JsModule.kt)

**Problem**: The JSON payload contained modifier types (`tint`, `coordinateSpace`, `visualEffect`) that weren't registered in the `RuntimeTypeAdapterFactory`, causing deserialization failures.

**Fix**:
- Created three new no-op modifier classes: `TintModifier`, `CoordinateSpaceModifier`, `VisualEffectModifier`
- Registered them in `JsModule.componentGson()` RuntimeTypeAdapterFactory

**New Files**:
- `base-js/.../model/modifier/TintModifier.kt`
- `base-js/.../model/modifier/CoordinateSpaceModifier.kt`
- `base-js/.../model/modifier/VisualEffectModifier.kt`

**Modified**: `base-js/.../di/JsModule.kt`

---

### 5. RoundedRectangle Default Color (RoundedRectangleView.kt)

**Problem**: `RoundedRectangleView` defaulted to `Color.LightGray` when no fill color was specified. In SwiftUI, shapes default to the foreground color (typically black in light mode, white in dark mode). The Sheet component's RoundedRectangle had no explicit fill and was rendering as light gray instead of the correct foreground color.

**Fix**: Changed default color from `Color.LightGray` to `getForegroundColor()` (which resolves the current foreground style from modifiers).

**File**: `base-js/.../composables/RoundedRectangleView.kt`

---

### 6. FrameModifier Infinity Handling (FrameModifier.kt)

**Problem**: SwiftUI uses `.infinity` for frame dimensions to mean "fill available space" (e.g., `frame(maxWidth: .infinity)`). The Android side received these as `Infinity` float values and tried to set pixel dimensions to infinity, causing layout issues.

**Fix**: Added handling in `FrameModifier.buildModifier()` to convert `Float.POSITIVE_INFINITY` width/height to `fillMaxWidth()` / `fillMaxHeight()` respectively, instead of using them as literal dp values.

**File**: `base-js/.../model/modifier/FrameModifier.kt`

---

### 7. Background Views matchParentSize (ComponentView.kt)

**Problem**: Background components (applied via `.background()` modifier) were not filling the parent component's full size, causing backgrounds to appear as small rectangles.

**Fix**: In the background rendering code within `ComponentView`, wrapped background components in a `Box(Modifier.matchParentSize())` with `FillMaxSize` propagated to children, so backgrounds fill the entire parent bounds.

**File**: `base-js/.../composables/ComponentView.kt`

---

### 8. GeometryReaderView fillMaxSize (GeometryReaderView.kt)

**Problem**: GeometryReader in SwiftUI always fills all available space. The Compose equivalent was using `fillMaxWidth()` only.

**Fix**: Changed to `fillMaxSize()` so it fills both width and height, matching SwiftUI behavior.

**File**: `base-js/.../composables/GeometryReaderView.kt`

---

### 9. JsFeedScreen fillMaxSize (JsFeedScreen.kt)

**Problem**: The root feed screen container was using `wrapContentSize()`, constraining the rendered component tree.

**Fix**: Changed to `fillMaxSize()` so the root container fills the screen.

**File**: `feature-home/.../JsFeedScreen.kt`

---

### 10. Offset NaN Hides Component (OffsetModifier.kt)

**Problem**: The Sheet component had `offset(y: "NaN")`. After fix #3, NaN was preserved as `Float.NaN`, but the offset modifier converted it to `0f`, making the Sheet visible at y=0. This caused an opaque black RoundedRectangle card to cover the text content.

**Key Insight**: In SwiftUI, `offset(y: .nan)` effectively makes the view invisible/non-rendered. The NaN offset is used as a mechanism to hide the Sheet overlay.

**Fix**: When offset x or y is `NaN`, return `Modifier.graphicsLayer(alpha = 0f)` to hide the component entirely, instead of positioning it at (0, 0).

**File**: `base-js/.../model/modifier/OffsetModifier.kt`

---

### 11. ContentScale Default (ModifierExt.kt)

**Problem**: The logo image was stretched/distorted because `getContentScale()` defaulted to `ContentScale.FillBounds` when no explicit scaling modifier was present. `FillBounds` stretches the image to fill the frame without preserving aspect ratio.

**Key Insight**: SwiftUI's `Image` defaults to no scaling (original size), but the closest Compose equivalent for general use is `ContentScale.Fit`, which preserves aspect ratio while fitting within bounds.

**Fix**: Changed default return value of `getContentScale()` from `ContentScale.FillBounds` to `ContentScale.Fit`.

**File**: `base-js/.../composables/ext/ModifierExt.kt`

---

### 12. Alignment Defaults: Center → TopStart (ComponentView.kt)

**Problem**: Logo and text were centered horizontally instead of left-aligned. Multiple layers of centering were responsible.

**Key Insight**: SwiftUI defaults to **top-leading** alignment. Compose's `wrapContentSize()` defaults to **Center** alignment, and `Box` defaults `contentAlignment` to **Center**. Every place these defaults were used needed to be changed.

**Fix** (multiple locations in ComponentView.kt):
- `NonModifiedComponent`: Changed `contentAlignment` from `Alignment.Center` to `Alignment.TopStart`
- `NonModifiedComponent`: Changed `wrapContentSize()` to `wrapContentSize(Alignment.TopStart)`
- `addWrapIfNoFrame()`: Changed `wrapContentSize()` to `wrapContentSize(Alignment.TopStart)`
- `wrapContentSize()` helper: Changed to `wrapContentSize(Alignment.TopStart)`

**File**: `base-js/.../composables/ComponentView.kt`

---

### 13. Remove WrapContentSize from NonModifiedComponent Subset (ComponentView.kt)

**Problem**: Even after changing alignment to TopStart, the old Center-aligned `WrapContentSize` from `addWrapIfNoFrame()` was being applied through `buildModifierFromSubset()` in NonModifiedComponent, which included `LocalModifier.WrapContentSize::class` in its include list. This Center-aligned modifier was processed BEFORE the explicit TopStart one.

**Fix**: Removed `LocalModifier.WrapContentSize::class` from the `include` list in `NonModifiedComponent`'s `buildModifierFromSubset()` call. The WrapContentSize is now only applied via the explicit `.then(if (!hasFill) Modifier.wrapContentSize(Alignment.TopStart) else Modifier)` chain.

**File**: `base-js/.../composables/ComponentView.kt`

---

### 14. ColumnView fillMaxWidth (ColumnView.kt)

**Problem**: VStack (Column) children were left-aligned via `horizontalAlignment = Alignment.Start`, but the Column itself was only as wide as its widest child (intrinsic width). This meant left-alignment had no visible effect since the Column was already minimum-width.

**Fix**: Added `.fillMaxWidth()` to the Column modifier so it fills available width, allowing `horizontalAlignment = Start` to visually left-align children.

**File**: `base-js/.../composables/ColumnView.kt`

---

### 15. GroupView: Layout-Transparent Container (GroupView.kt)

**Problem**: GroupView was rendering as a `Column` with `horizontalAlignment = CenterHorizontally`, acting as a centering layout container.

**Key Insight**: In SwiftUI, `Group` is **NOT a layout container**. It's a transparent wrapper that simply passes its children to the parent layout. It does not add any positioning, alignment, or sizing of its own. Rendering it as a Column with centering was fundamentally wrong.

**Fix**: Completely rewrote GroupView to simply iterate children and render each one directly via `ComponentView`, passing through the parent's modifiers without wrapping in any layout container.

```kotlin
@Composable
fun GroupView(...) {
    component.props.children?.forEach { child ->
        child?.let {
            ComponentView(jsRuntime, child, onUiEvent, modifiers = modifiers)
        }
    }
}
```

**File**: `base-js/.../composables/GroupView.kt`

---

### 16. FrameModifier FillMaxSize Filtering (ComponentView.kt)

**Problem**: The logo was still centered even after all alignment fixes. Root cause: `BoxView` (ZStack) propagates `FillMaxSize(fillMaxWidth())` to its children. When a child has a `FrameModifier` with explicit width/height (e.g., the logo frame at 121.25x35), the `fillMaxWidth()` was applied to the outer Box wrapping the frame content, making it full-width. The frame's explicit width only constrained the inner content, so the outer Box was full-width with the logo centered within it.

**Key Insight**: If a component has a frame with explicit dimensions, it should NOT inherit `FillMaxSize` / `FillMaxWidth` from parent containers. The explicit frame dimensions should take precedence.

**Fix**: In the `FrameModifier` composable within ComponentView, added logic to detect when a frame has explicit width or height, and filter out `FillMaxSize` and `FillMaxWidth` local modifiers before building the Compose modifier chain.

```kotlin
val hasExplicitSize = frameModifier?.props?.width != null || frameModifier?.props?.height != null
val effectiveModifiers = if (hasExplicitSize) {
    modifiers.filter { it !is LocalModifier.FillMaxSize && it !is LocalModifier.FillMaxWidth }
} else {
    modifiers
}
```

**File**: `base-js/.../composables/ComponentView.kt`

---

## Summary of SwiftUI vs Compose Behavioral Differences

| Behavior | SwiftUI | Compose (before fix) | Fix Applied |
|---|---|---|---|
| Default alignment | Top-leading | Center | Changed to TopStart |
| Image scaling default | No scaling (original size) | FillBounds (stretch) | Changed to Fit |
| Group container | Transparent (no layout) | Column with centering | Pass-through children |
| HStack child sizing | Intrinsic | Equal weight (weight 1f) | Removed weight |
| VStack width | Fills available width | Intrinsic width | Added fillMaxWidth |
| Shape default color | Foreground color | LightGray | Use getForegroundColor() |
| Frame .infinity | Fill available space | Literal infinity dp | Convert to fillMax |
| Offset .nan | View not rendered | Offset to (0,0) | Hide with alpha=0 |
| Background sizing | Fills parent bounds | Intrinsic size | matchParentSize |
| GeometryReader | Fills all available space | fillMaxWidth only | fillMaxSize |
| Frame with explicit size + parent fill | Frame size wins | Both applied (fill + frame) | Filter out fill modifiers |

## Files Modified

### Modified (13 files):
1. `base-js/.../composables/BoxView.kt`
2. `base-js/.../composables/ColumnView.kt`
3. `base-js/.../composables/ComponentView.kt` (most changes)
4. `base-js/.../composables/GeometryReaderView.kt`
5. `base-js/.../composables/GroupView.kt` (rewritten)
6. `base-js/.../composables/RoundedRectangleView.kt`
7. `base-js/.../composables/RowView.kt`
8. `base-js/.../composables/ext/ModifierExt.kt`
9. `base-js/.../di/JsModule.kt`
10. `base-js/.../model/ext/StringExt.kt`
11. `base-js/.../model/modifier/FrameModifier.kt`
12. `base-js/.../model/modifier/OffsetModifier.kt`
13. `feature-home/.../feed/screens/js/JsFeedScreen.kt`

### New (3 files):
14. `base-js/.../model/modifier/CoordinateSpaceModifier.kt`
15. `base-js/.../model/modifier/TintModifier.kt`
16. `base-js/.../model/modifier/VisualEffectModifier.kt`

## Lessons Learned

1. **SwiftUI defaults to top-leading alignment everywhere**. In Compose, you must explicitly set `Alignment.TopStart` in `Box`, `wrapContentSize()`, and `contentAlignment` parameters to match.

2. **Modifier accumulation order matters**. When modifiers are collected from multiple sources (addWrapIfNoFrame, buildModifierFromSubset, explicit .then() chains), the order and which modifiers are included can cause conflicts. A Center-aligned WrapContentSize from one source can override a TopStart from another.

3. **SwiftUI Group is NOT a layout container**. It's purely organizational — it groups views for applying modifiers or conditional logic but contributes no layout of its own. Children are passed directly to the parent container.

4. **Parent fill modifiers must not override explicit frame dimensions**. When a ZStack/BoxView propagates `fillMaxWidth()` to children, children with explicit frame sizes should not inherit that fill behavior.

5. **NaN values in the payload are intentional signals**, not errors. `offset(y: NaN)` means "don't render this view." Handle NaN as a semantic value, not a fallback-to-zero.

6. **ContentScale matters for images**. SwiftUI images don't scale by default; Compose's `FillBounds` aggressively stretches. `Fit` is a safer default.

7. **Debugging rendering issues requires understanding the full modifier chain**. A component's final appearance depends on modifiers from: its own definition, parent propagation (FillMaxSize), NonModifiedComponent wrapping, addWrapIfNoFrame, and buildModifier/buildModifierFromSubset processing.
