# Old Gartic Donor Audit

## Purpose

The old Gartic/Sketchware project must not be copied into SolumDraw as architecture. It should be treated as a donor for drawing ideas only.

This document defines what must be extracted from the old project before implementing the next real drawing core.

## Current finding

The current clean SolumDraw planner is useful as a foundation, but it is not a 1:1 reconstruction engine yet.

Current foundation:

- safe image import;
- runtime and crash logs;
- source overlay / white canvas preview;
- connected regions;
- fitted preview coordinates;
- edge pass;
- staged counters.

Current weakness:

- sparse output;
- regions are drawn as outlines/sweeps, not as real reconstruction;
- no virtual canvas;
- no target-vs-current error map;
- no residual pass;
- no scoring of whether a stroke improves the image.

## Why the old algorithm looked closer to 1:1

The old algorithm likely appeared more accurate because it used dense reconstruction behavior:

```text
target image -> many small draw operations -> repeated coverage/correction -> dense output
```

That approach can look close to the source, but it often creates printer-like artifacts:

- strict zigzag routes;
- uniform stroke spacing;
- mechanical color order;
- excessive pixel-level tracing;
- little human staging.

## What to preserve conceptually

Extract these ideas only:

1. Palette handling
   - how colors were selected;
   - whether colors were quantized;
   - how many colors were used per pass;
   - whether similar colors were merged.

2. Coverage strategy
   - how empty zones were filled;
   - how dense passes were generated;
   - how transparent / dark / bright areas were handled.

3. Residual behavior
   - whether the old project compared missing pixels;
   - whether it revisited failed zones;
   - whether it had cleanup passes.

4. Route ordering
   - whether it grouped by color, by area, by proximity, or by scanline;
   - what caused printer-like behavior;
   - what can be randomized or staged.

5. Tool usage
   - brush size;
   - fill;
   - alpha;
   - eraser or repair;
   - color picking.

6. Preview/replay details
   - coordinate mapping;
   - speed mode;
   - draw event format;
   - timing metadata.

## What must not be copied

Do not copy these parts into SolumDraw:

- Sketchware monolithic MainActivity architecture;
- huge JavaScript strings inside Java;
- cookie/session/sync hacks;
- WebView automation mixed with image analysis;
- printer-like strict scanline core as the final algorithm;
- unmanaged global state;
- unlogged failure paths.

## Required donor files

When available, place selected old-reference material under:

```text
docs/donor_old_gartic/
```

Allowed files:

```text
old_algorithm_notes.md
old_method_names.md
old_stroke_format_sample.json
old_route_examples.md
```

Avoid committing raw private/session material or huge old dumps.

## Audit checklist

Before implementing dense reconstruction, answer these:

- What was the old minimum brush size?
- What was the old maximum brush size?
- Did it draw by color first or by spatial region first?
- Did it use fill or only strokes?
- Did it use alpha/opacity?
- Did it have multiple quality modes?
- How many operations did it generate for a 512px image?
- Did it generate paths or single touch points?
- Did it retry missing areas?
- Which part caused the printer/zigzag look?

## Conclusion

The old project should guide the density and residual/correction strategy. SolumDraw should implement that strategy cleanly with a virtual canvas and error map instead of importing the old architecture.
