#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import sys
import zipfile
from pathlib import Path

FILES = [
    "benchmark_results.csv",
    "mistakes.csv",
    "benchmark_summary.json",
    "BENCHMARK_REPORT.md",
    "predictions.jsonl",
]

def latest_run(runs: Path) -> Path:
    if not runs.exists():
        raise FileNotFoundError(f"runs dir not found: {runs}")
    dirs = [p for p in runs.iterdir() if p.is_dir()]
    if not dirs:
        raise FileNotFoundError(f"no run dirs in: {runs}")
    return max(dirs, key=lambda p: p.stat().st_mtime)

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", default=str(Path.home() / "SolumDraw/datasets/SolumDrawDataset_v1/benchmark_runs"))
    ap.add_argument("--download", default=str(Path.home() / "storage/downloads"))
    args = ap.parse_args()

    runs = Path(args.runs).expanduser()
    download = Path(args.download).expanduser()
    download.mkdir(parents=True, exist_ok=True)

    try:
        run = latest_run(runs)
        stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
        out = download / f"solumdraw_benchmark_latest_{stamp}.zip"
        added = []
        missing = []

        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
            for name in FILES:
                p = run / name
                if p.is_file():
                    z.write(p, name)
                    added.append(name)
                else:
                    missing.append(name)
            z.writestr(
                "ZIP_INFO.txt",
                "SolumDraw benchmark lightweight ZIP\n"
                f"run_dir={run}\n"
                f"added={','.join(added) if added else 'none'}\n"
                f"missing={','.join(missing) if missing else 'none'}\n"
            )

        if not added:
            out.unlink(missing_ok=True)
            raise FileNotFoundError("latest run has no expected benchmark files")

        print(f"RUN_DIR={run}")
        print(f"ZIP_READY={out}")
        return 0
    except Exception as e:
        print(f"ZIP_FAILED={e}", file=sys.stderr)
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
