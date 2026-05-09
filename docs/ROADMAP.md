# SolumDraw Roadmap

## Patch 01 - Clean foundation

Status: current PR.

Goal: create a clean Android/Gradle base with a separated human stroke planner and preview/export loop.

## Patch 02 - Real shape extraction

Goal: replace coarse palette point sampling with real image structure.

Planned work:

- Edge detection.
- Region island extraction.
- Large area fill planning.
- Route grouping by form, not only by color.
- Separate background, mass, contour, detail passes.

## Patch 03 - Humanizer v1

Goal: reduce printer-like behavior.

Planned work:

- Variable stroke speed metadata.
- Human pauses.
- Imperfect but controlled path wobble.
- Return-to-detail behavior.
- Less uniform stroke spacing.

## Patch 04 - Gartic bridge

Goal: replay generated stroke plans into a real drawing surface/WebView.

Planned work:

- Canvas target detection.
- Coordinate mapping.
- Tool selection abstraction.
- Brush/fill/color replay.

## Patch 05 - ML scorer foundation

Goal: make a small model score which next stroke improves the visible result most.

Planned work:

- Dataset export from generated plans.
- Error/importance map.
- Small on-device friendly scorer design.
