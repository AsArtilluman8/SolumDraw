# SolumDraw Analyzer + Benchmark v2

## Purpose

This patch adds a second analyzer runner.

v1 proved the benchmark loop works, but the first rule-based analyzer was too sticky toward `pixel_art`, `portrait_character`, `anime_manga`, and `vector_flat`.

v2 keeps the same output format but changes scoring:

- coarse-to-fine gates;
- stricter `pixel_art`;
- stricter `portrait_character`;
- separate layout/text/diagram proxies;
- separate surface/pattern/halftone proxies;
- stronger class suppressors for obvious false positives.

## Files

- `tools/solumdraw_analyzer_benchmark_v2.py`

It depends on:

- `tools/solumdraw_analyzer_benchmark_v1.py`

v2 reuses v1 feature extraction and single HTML renderer, then adds new class scoring and v2 feature gates.

## Commands

Quick benchmark:

```bash
cd ~/SolumDraw && \
python3 tools/solumdraw_analyzer_benchmark_v2.py benchmark \
  --dataset ~/SolumDraw/datasets/SolumDrawDataset_v1 \
  --limit 20
```

Full benchmark:

```bash
cd ~/SolumDraw && \
python3 tools/solumdraw_analyzer_benchmark_v2.py benchmark \
  --dataset ~/SolumDraw/datasets/SolumDrawDataset_v1
```

Single image:

```bash
cd ~/SolumDraw && \
python3 tools/solumdraw_analyzer_benchmark_v2.py single \
  --image ~/SolumDraw/datasets/SolumDrawDataset_v1/images/000013.jpg \
  --html
```

## Outputs

v2 writes the same benchmark structure:

```text
benchmark_results.csv
predictions.jsonl
mistakes.csv
benchmark_summary.json
BENCHMARK_REPORT.md
```

The run folder name starts with:

```text
run_v2_YYYYMMDD_HHMMSS
```

## Expected result

v1 result on the first dataset was around:

```text
top1_accuracy ≈ 0.10
top3_accuracy ≈ 0.19
```

v2 is expected to improve calibration and reduce massive false positives. It may still be weak because this is not ML and has no object detector/OCR yet.

The important files to inspect after v2:

- `BENCHMARK_REPORT.md`
- `mistakes.csv`
- `benchmark_summary.json`
