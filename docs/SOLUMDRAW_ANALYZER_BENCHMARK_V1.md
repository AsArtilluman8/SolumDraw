# SolumDraw Analyzer + Benchmark v1

## Purpose

This patch adds a first benchmark-ready analyzer for SolumDraw.

The analyzer is intentionally simple and local. It does not use ML yet. It extracts pixel features and predicts image genre/type with rule-based scoring.

Benchmark loop:

1. Analyze image pixels only.
2. Predict top-1 and top-3 classes.
3. Produce feature tags and a rough drawing route.
4. Compare predictions with dataset JSON labels.
5. Report mistakes and common confusions.
6. Improve analyzer from measured errors.

## Files

- `tools/solumdraw_analyzer_benchmark_v1.py`

## Batch outputs

The benchmark creates:

```text
~/SolumDraw/datasets/SolumDrawDataset_v1/benchmark_runs/run_YYYYMMDD_HHMMSS/
```

Inside:

```text
benchmark_results.csv
predictions.jsonl
mistakes.csv
benchmark_summary.json
BENCHMARK_REPORT.md
```

## Important rule

The analyzer must receive only bitmap/image pixels. It must not read filename, folder, JSON label, CSV, prompt, source, or metadata.

The benchmark runner reads labels only after prediction, to score correctness.

## Commands

Run full benchmark:

```bash
cd ~/SolumDraw && \
python3 tools/solumdraw_analyzer_benchmark_v1.py benchmark \
  --dataset ~/SolumDraw/datasets/SolumDrawDataset_v1
```

Run quick test on first 20 images:

```bash
cd ~/SolumDraw && \
python3 tools/solumdraw_analyzer_benchmark_v1.py benchmark \
  --dataset ~/SolumDraw/datasets/SolumDrawDataset_v1 \
  --limit 20
```

Run single image analysis with visual HTML:

```bash
cd ~/SolumDraw && \
python3 tools/solumdraw_analyzer_benchmark_v1.py single \
  --image ~/SolumDraw/datasets/SolumDrawDataset_v1/images/000013.jpg \
  --html
```

## Expected first result

This is v1, so errors are expected.

Useful target for the first iteration:

- Top-1 >= 35-55% on 36 classes is useful for rule-based v1.
- Top-3 >= 55-75% is useful enough to start improving class confusions.
- The main value is `mistakes.csv` and `common_confusions`.

## Next improvement targets

Likely weak spots:

- anime_manga vs pencil_drawing vs lineart_sketch
- ui_screenshot vs diagram_chart
- vector_flat vs logo_icon
- texture_pattern vs pattern_seamless
- vfx_glow_magic vs space_scifi_bg
- portrait_character vs photo_general
