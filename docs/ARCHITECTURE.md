# SolumDraw Architecture

## Principle

SolumDraw must not inherit the old Sketchware/Gartic architecture. The old project is a donor for concepts only.

## Pipeline

```text
ImageAnalyzer
-> PalettePlanner
-> ShapeExtractor
-> StrokePlanner
-> HumanPipeline
   -> Sculptor
   -> Potter
   -> Grinder
   -> Polisher
-> Humanizer
-> Preview
-> Export
-> future GarticBridge
```

## Patch 01 scope

Patch 01 creates only the clean foundation:

- Android Gradle project.
- Image import.
- Quantized palette sampling.
- Basic staged stroke planning.
- Human order variation.
- Preview canvas.
- JSON export.

## What is intentionally not included yet

- Real contour extraction.
- Flood-fill region islands.
- Gartic automation bridge.
- ML scorer.
- Text-to-image generation.
- Old cookie/sync/WebView hacks.

## Role model

### Sculptor

Large background, silhouette, big masses.

### Potter

Form refinement, shape closure, region cleanup.

### Grinder

Medium details, shadows, outlines.

### Polisher

Small accents, highlights, final readable features.
