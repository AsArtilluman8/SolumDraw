# SolumDraw Roadmap

## Patch 01 - Clean foundation

Status: merged.

Goal: create a clean Android/Gradle base with a separated human stroke planner and preview/export loop.

## Patch 02A - Crash debug log

Status: merged.

Goal: every fatal crash should write a readable text report to Download.

Output files:

- `solumdraw_last_boot.txt`
- `solumdraw_crash_YYYYMMDD_HHMMSS.txt`
- `solumdraw_crash_YYYYMMDD_HHMMSS_handled.txt`

## Patch 02B - Image import diagnostics and bitmap guard

Status: current PR.

Goal: make image import safe before deeper drawing algorithms.

Output files:

- `solumdraw_runtime_log.txt`
- `solumdraw_stroke_plan_patch02b.json`

Included checks:

- Original image size.
- Decoded image size.
- Bitmap sample size.
- Runtime memory summary.
- Plan build duration.

## Patch 03A - Shape extraction foundation

Status: added to current PR.

Goal: replace coarse palette point sampling with connected color regions.

Included work:

- Quantized color labels.
- Flood-fill connected region extraction.
- Region bounds, area, density, and sample points.
- Stage ranking by region size/density.
- Planner now emits region-based Sculptor/Potter/Grinder/Polisher actions.

## Patch 03B - Edge and contour pass

Goal: add real edge/outline extraction on top of regions.

Planned work:

- Luma/contrast edge detection.
- Region border tracing.
- Face/important-detail priority hints.
- Separate outline strokes from fill strokes.

## Patch 04 - Sculptor and Potter real passes

Goal: make the early drawing process recognizable and human-like.

Planned work:

- Background and silhouette planning.
- Large form closure.
- Region cleanup.
- Stable proportional pass.

## Patch 05 - Grinder and Polisher real passes

Goal: add details without printer-like behavior.

Planned work:

- Contours.
- Shadows.
- Medium details.
- Highlights.
- Important feature accents.

## Patch 06 - Humanizer v1

Goal: reduce printer-like behavior.

Planned work:

- Variable stroke speed metadata.
- Human pauses.
- Imperfect but controlled path wobble.
- Return-to-detail behavior.
- Less uniform stroke spacing.

## Patch 07 - Timeline replay controls

Goal: inspect the drawing order.

Planned work:

- Play and pause.
- Step forward.
- Speed slider.
- Stage filter.
- Progress counter.

## Patch 08 - Gartic bridge foundation

Goal: replay generated stroke plans into a real drawing surface/WebView.

Planned work:

- Canvas target detection.
- Coordinate mapping.
- Tool selection abstraction.
- Brush/fill/color replay.

## Patch 09 - Tool brain v1

Goal: choose brush/fill/alpha/color intelligently.

## Patch 10 - ML scorer foundation

Goal: make a small model score which next stroke improves the visible result most.

Planned work:

- Dataset export from generated plans.
- Error/importance map.
- Small on-device friendly scorer design.
