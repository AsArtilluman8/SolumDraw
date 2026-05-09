# SolumDraw Roadmap

## Patch 01 - Clean foundation

Status: merged.

Goal: create a clean Android/Gradle base with a separated human stroke planner and preview/export loop.

## Patch 02A - Crash debug log

Status: merged.

Goal: every fatal crash should write a readable text report to Download.

## Patch 02B/03A-03F - Import diagnostics and stroke planner foundation

Status: merged.

Included:

- safe image import;
- runtime log;
- connected color regions;
- fitted preview coordinates;
- white canvas preview mode;
- edge/detail pass;
- basic Sculptor/Potter/Grinder/Polisher counters.

Current limitation:

- output is still sparse on white canvas;
- region/edge planning is not enough for near 1:1 reconstruction;
- the next core must use virtual canvas + error map + residual correction.

## Patch 04A - Donor audit and reconstruction plan

Status: current PR.

Goal: stop tuning the wrong mechanism and define the correct reconstruction architecture.

Included documents:

- `docs/OLD_GARTIC_DONOR_AUDIT.md`
- `docs/RECONSTRUCTION_ENGINE_PLAN.md`

## Patch 04B - VirtualCanvas simulator

Goal: create an offscreen drawable canvas representing what SolumDraw has already drawn.

Planned work:

- `TargetImage` wrapper;
- `VirtualCanvas` bitmap;
- apply `StrokeAction` to virtual canvas;
- initial metrics/debug export;
- no UI overhaul.

## Patch 04C - ErrorMap v1

Goal: compare target image to virtual canvas.

Planned work:

- pixel/cell error;
- top error zones;
- debug summary;
- future candidate scoring input.

## Patch 04D - Dense residual pass v1

Goal: add real density by drawing where error remains high.

Planned work:

- residual correction strokes;
- 300-1500 actions depending on mode;
- fill missing zones;
- avoid strict scanline printer behavior.

## Patch 04E - Anti-printer humanizer

Goal: keep residual accuracy while making the route less mechanical.

Planned work:

- group by zone first, color second;
- delayed return to important zones;
- varied stroke length/direction;
- controlled route noise.

## Patch 05 - Tool brain v1

Goal: choose brush/fill/alpha/color intelligently.

Planned work:

- fill for large masses;
- brush for medium regions;
- thin line for detail;
- alpha/opacity metadata.

## Patch 06 - Timeline replay controls

Goal: inspect the drawing order.

Planned work:

- play/pause;
- step forward;
- speed slider;
- stage filter;
- progress counter.

## Patch 07 - Gartic bridge foundation

Goal: replay generated stroke plans into a real drawing surface/WebView.

Planned work:

- canvas target detection;
- coordinate mapping;
- tool selection abstraction;
- brush/fill/color replay.

## Patch 08 - ML scorer foundation

Goal: make a small model score which next stroke improves the visible result most.

Planned work:

- dataset export from generated plans;
- error/importance map;
- small on-device friendly scorer design.
