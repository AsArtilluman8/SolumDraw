# SolumDraw

SolumDraw is a clean Android/Gradle project for human-like image-to-canvas drawing.

The goal is not to copy the old Sketchware/Gartic code. The old project is used only as a donor for useful ideas: palette extraction, segmented regions, preview, and staged drawing.

## Core idea

```text
Image -> analysis -> stroke plan -> human pipeline -> preview/export -> future Gartic bridge
```

The first architecture uses four drawing roles:

```text
Sculptor -> Potter -> Grinder -> Polisher
```

- **Sculptor**: large masses, silhouette, rough base.
- **Potter**: shape correction, form closure, region refinement.
- **Grinder**: medium details, contours, shadows.
- **Polisher**: small details, highlights, final accents.

## Patch 01

Clean Android foundation with:

- Java/Gradle project skeleton.
- Image import.
- Stroke plan generation.
- Human stage architecture.
- Preview canvas.
- JSON stroke plan export.

Merge policy: PRs should not be merged without explicit owner approval.
