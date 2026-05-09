# Reconstruction Engine Plan

## Problem

The current region/edge planner is not enough for near 1:1 drawing. It can detect coarse shapes, but it cannot know what is missing because it never compares the drawn result with the target image.

## Correct direction

SolumDraw needs a reconstruction loop:

```text
Target image
-> Virtual canvas
-> Error map
-> Stroke candidate generator
-> Stroke scorer
-> Best stroke picker
-> Apply stroke
-> Repeat
```

This is the clean replacement for the old dense/printer-like algorithm.

## Core modules

### 1. TargetImage

Stores a downscaled working copy of the input image.

Responsibilities:

- stable width/height;
- pixel access;
- luma/color helpers;
- optional palette quantization;
- importance masks later.

### 2. VirtualCanvas

An offscreen bitmap that represents what SolumDraw has already drawn.

Responsibilities:

- start from paper/background color;
- apply brush strokes;
- apply fill strokes later;
- expose pixels for error comparison;
- optionally export debug preview.

### 3. ErrorMap

Compares target image against virtual canvas.

Responsibilities:

- per-pixel color error;
- local error sum;
- find high-error cells;
- reject already-correct areas;
- rank zones by visual importance.

### 4. StrokeCandidateGenerator

Generates possible strokes for bad zones.

Candidate types:

- short line;
- curved/polyline segment;
- hatch fill;
- blob/dab;
- future fill operation.

Candidate parameters:

- color;
- brush size;
- alpha;
- start/end points;
- stage tag;
- estimated cost.

### 5. StrokeScorer

Tests whether a candidate improves the virtual canvas.

Scoring idea:

```text
score = error_before - error_after - cost_penalty - printer_penalty
```

Reject if score is low or if candidate makes output worse.

### 6. ResidualPlanner

Runs the iterative loop.

Passes:

1. Sculptor base mass reconstruction.
2. Potter shape closure.
3. Grinder residual detail.
4. Polisher high-error small accents.

## Quality modes

### Fast

- fewer iterations;
- larger brush;
- fewer colors;
- lower candidate count.

### Natural

- more iterations;
- less strict route order;
- controlled wobble;
- delayed detail return.

### Accurate / future

- many iterations;
- high residual correction;
- useful for offline export.

## Anti-printer rules

The reconstruction loop must avoid copying the old printer behavior.

Rules:

- Do not draw strictly scanline order.
- Do not finish all of one color before moving spatially if it looks mechanical.
- Group by visual object/zone first, color second.
- Revisit important zones later.
- Vary stroke length and direction.
- Use stage order: mass -> form -> detail -> polish.
- Add route noise only after the geometry is good.

## Patch sequence

### Patch 04B - VirtualCanvas simulator

Add:

- `reconstruct/TargetImage.java`
- `reconstruct/VirtualCanvas.java`
- basic `applyStroke(StrokeAction)`
- debug export of virtual canvas metrics

### Patch 04C - ErrorMap v1

Add:

- `reconstruct/ErrorMap.java`
- per-cell error ranking
- top error zone report

### Patch 04D - Dense residual pass v1

Add:

- `reconstruct/ResidualPlanner.java`
- iterative correction strokes
- 300-1500 action output depending on mode

### Patch 04E - Anti-printer humanizer

Add:

- route shuffle by zone;
- stroke length variation;
- non-scanline candidate choice;
- stage-aware revisit.

## Definition of success

White canvas preview should become recognizable without the source overlay.

Minimum target:

- large shapes are filled, not only outlined;
- empty zones are reduced;
- action count is higher but still responsive;
- Fast mode remains usable on phone;
- Natural mode looks less mechanical than old printer logic.
