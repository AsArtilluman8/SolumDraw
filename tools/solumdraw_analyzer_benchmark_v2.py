#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SolumDraw Analyzer + Benchmark v2

Main change vs v1:
- coarse-to-fine scoring;
- class-specific suppressors;
- less sticky pixel_art / portrait_character;
- same benchmark output format.

This is still rule-based, not ML.
Analyzer still uses bitmap pixels only.
"""

from __future__ import annotations

import argparse
import csv
import datetime as _dt
import importlib.util
import json
import math
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, List, Tuple


def _load_v1():
    here = Path(__file__).resolve().parent
    v1_path = here / "solumdraw_analyzer_benchmark_v1.py"
    if not v1_path.exists():
        raise SystemExit(f"Missing v1 dependency: {v1_path}")
    spec = importlib.util.spec_from_file_location("solumdraw_analyzer_benchmark_v1", str(v1_path))
    mod = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(mod)
    return mod


v1 = _load_v1()
DEFAULT_CLASSES = list(v1.DEFAULT_CLASSES)
CLASS_DRAW_PLANS = dict(v1.CLASS_DRAW_PLANS)


def clamp01(x: float) -> float:
    return max(0.0, min(1.0, float(x)))


def safe_div(a: float, b: float, default: float = 0.0) -> float:
    return default if b == 0 else a / b


def sigmoid01(x: float, center: float, scale: float) -> float:
    if scale <= 0:
        return 1.0 if x >= center else 0.0
    try:
        return 1.0 / (1.0 + math.exp(-(x - center) / scale))
    except OverflowError:
        return 0.0 if x < center else 1.0


def derive_v2_features(f: Dict[str, Any]) -> Dict[str, float]:
    """Derive extra gate features from v1 pixel features."""
    flat = f["flat_score"]
    line = f["lineart_score"]
    photo = f["photo_score"]
    layout = f["layout_score"]
    tex = f["texture_score"]
    glow = f["glow_score"]
    pixel = f["pixel_score"]
    trans = f["transparent_score"]
    face = f["face_like_score"]
    anime = f["anime_score"]
    text = f["text_score"]
    pattern = f["pattern_score"]
    noisy = f["noisy_score"]
    gray = f["grayscale_ratio"]
    sat = f["mean_saturation"]
    edge = f["edge_density"]
    dark = f["dark_ratio"]
    light = f["light_ratio"]
    entropy = f["entropy_norm"]
    direction = f["line_direction_bias"]
    top_color = f["top_color_ratio"]
    skin = f["skin_like_ratio"]

    # Stricter detectors/gates.
    pixel_strict = clamp01(
        0.40 * pixel +
        0.30 * top_color +
        0.20 * (1.0 - entropy) +
        0.10 * sigmoid01(edge, 0.18, 0.05)
    )
    # Pixel must be flat/limited-palette. Penalize photo/texture.
    pixel_strict *= clamp01(1.20 - 0.70 * photo - 0.40 * tex)

    portrait_strict = clamp01(
        0.45 * face +
        0.25 * sigmoid01(skin, 0.08, 0.04) +
        0.15 * sigmoid01(edge, 0.08, 0.04) +
        0.15 * sigmoid01(light, 0.10, 0.08)
    )
    # Avoid false portrait on architecture/posters/flat maps.
    portrait_strict *= clamp01(1.10 - 0.55 * layout - 0.45 * flat)

    line_drawing_group = clamp01(0.45 * line + 0.25 * gray + 0.18 * light + 0.12 * anime)
    layout_group = clamp01(0.45 * layout + 0.35 * text + 0.20 * flat)
    surface_group = clamp01(0.48 * tex + 0.34 * pattern + 0.18 * entropy)
    painting_group = clamp01(0.36 * tex + 0.24 * sat + 0.20 * entropy + 0.20 * (1.0 - flat))
    flat_symbol_group = clamp01(0.55 * flat + 0.25 * layout + 0.20 * (1.0 - tex))
    photo_scene_group = clamp01(0.50 * photo + 0.20 * entropy + 0.15 * (1.0 - flat) + 0.15 * (1.0 - trans))
    vfx_group = clamp01(0.55 * glow + 0.25 * dark + 0.20 * sat)
    space_group = clamp01(0.45 * dark + 0.30 * glow + 0.25 * entropy)

    dot_halftone_proxy = clamp01(0.45 * pattern + 0.30 * gray + 0.25 * line)
    ui_proxy = clamp01(0.50 * layout + 0.25 * text + 0.15 * flat + 0.10 * direction)
    diagram_proxy = clamp01(0.42 * layout + 0.32 * text + 0.16 * direction + 0.10 * light)
    text_proxy = clamp01(0.58 * text + 0.28 * light + 0.14 * gray)
    logo_proxy = clamp01(0.42 * flat + 0.25 * light + 0.20 * line + 0.13 * (1.0 - tex))

    return {
        "pixel_strict": round(pixel_strict, 5),
        "portrait_strict": round(portrait_strict, 5),
        "line_drawing_group": round(line_drawing_group, 5),
        "layout_group": round(layout_group, 5),
        "surface_group": round(surface_group, 5),
        "painting_group": round(painting_group, 5),
        "flat_symbol_group": round(flat_symbol_group, 5),
        "photo_scene_group": round(photo_scene_group, 5),
        "vfx_group": round(vfx_group, 5),
        "space_group": round(space_group, 5),
        "dot_halftone_proxy": round(dot_halftone_proxy, 5),
        "ui_proxy": round(ui_proxy, 5),
        "diagram_proxy": round(diagram_proxy, 5),
        "text_proxy": round(text_proxy, 5),
        "logo_proxy": round(logo_proxy, 5),
    }


def score_classes_v2(f: Dict[str, Any], classes: List[str]) -> Dict[str, float]:
    d = derive_v2_features(f)

    flat = f["flat_score"]
    line = f["lineart_score"]
    photo = f["photo_score"]
    layout = f["layout_score"]
    tex = f["texture_score"]
    glow = f["glow_score"]
    trans = f["transparent_score"]
    face = f["face_like_score"]
    anime = f["anime_score"]
    text = f["text_score"]
    pattern = f["pattern_score"]
    noisy = f["noisy_score"]
    gray = f["grayscale_ratio"]
    sat = f["mean_saturation"]
    edge = f["edge_density"]
    dark = f["dark_ratio"]
    light = f["light_ratio"]
    entropy = f["entropy_norm"]
    direction = f["line_direction_bias"]
    skin = f["skin_like_ratio"]
    alpha = f["alpha_non_opaque_ratio"]
    aspect = f["aspect"]

    # Coarse gates. They do not choose a class alone; they restrict classes.
    G = {
        "photo": d["photo_scene_group"],
        "portrait": d["portrait_strict"],
        "line": d["line_drawing_group"],
        "layout": d["layout_group"],
        "surface": d["surface_group"],
        "paint": d["painting_group"],
        "flat": d["flat_symbol_group"],
        "pixel": d["pixel_strict"],
        "vfx": d["vfx_group"],
        "space": d["space_group"],
    }

    raw = {}

    # Photo / object / environment.
    raw["photo_general"] = 0.52 * G["photo"] + 0.20 * photo + 0.13 * entropy + 0.15 * (1.0 - layout)
    raw["portrait_character"] = 0.60 * G["portrait"] + 0.20 * face + 0.12 * skin + 0.08 * anime
    raw["animal_creature"] = 0.44 * G["photo"] + 0.22 * tex + 0.18 * entropy + 0.16 * (1.0 - layout)
    raw["human_body_fullbody"] = 0.34 * anime + 0.26 * line + 0.20 * face + 0.20 * G["photo"]
    raw["landscape_environment"] = 0.40 * G["photo"] + 0.20 * entropy + 0.18 * tex + 0.12 * sat + 0.10 * (1.0 - face)
    raw["architecture_hardsurface"] = 0.35 * layout + 0.25 * photo + 0.22 * edge + 0.18 * direction
    raw["product_object"] = 0.34 * photo + 0.24 * light + 0.22 * flat + 0.20 * (1.0 - entropy)
    raw["still_life"] = 0.36 * photo + 0.27 * tex + 0.20 * entropy + 0.17 * sat

    # Drawing / media.
    raw["anime_manga"] = 0.45 * anime + 0.24 * line + 0.16 * face + 0.15 * G["line"]
    raw["lineart_sketch"] = 0.56 * line + 0.22 * light + 0.14 * gray + 0.08 * G["line"]
    raw["pencil_drawing"] = 0.40 * line + 0.26 * gray + 0.22 * tex + 0.12 * (1.0 - flat)
    raw["grayscale_ink"] = 0.36 * gray + 0.28 * line + 0.22 * dark + 0.14 * tex
    raw["ink_wash"] = 0.34 * gray + 0.23 * light + 0.21 * (1.0 - entropy) + 0.22 * tex
    raw["watercolor_paint"] = 0.35 * G["paint"] + 0.23 * (1.0 - edge) + 0.22 * sat + 0.20 * light
    raw["oil_painting"] = 0.42 * G["paint"] + 0.26 * tex + 0.18 * entropy + 0.14 * sat
    raw["digital_painting_concept"] = 0.36 * G["paint"] + 0.24 * glow + 0.22 * sat + 0.18 * entropy
    raw["abstract_art"] = 0.36 * entropy + 0.26 * sat + 0.22 * tex + 0.16 * (1.0 - face)

    # Flat / layout / graphic.
    raw["vector_flat"] = 0.44 * G["flat"] + 0.25 * flat + 0.17 * layout + 0.14 * (1.0 - tex)
    raw["logo_icon"] = 0.56 * d["logo_proxy"] + 0.22 * flat + 0.12 * light + 0.10 * (1.0 - entropy)
    raw["isometric_art"] = 0.40 * G["flat"] + 0.26 * layout + 0.20 * edge + 0.14 * sat
    raw["cartoon_comic"] = 0.32 * line + 0.27 * flat + 0.23 * layout + 0.18 * text
    raw["sticker_chibi"] = 0.36 * anime + 0.30 * flat + 0.18 * light + 0.16 * face
    raw["diagram_chart"] = 0.54 * d["diagram_proxy"] + 0.25 * layout + 0.21 * text
    raw["ui_screenshot"] = 0.58 * d["ui_proxy"] + 0.25 * layout + 0.17 * flat
    raw["game_ui_hud"] = 0.40 * layout + 0.26 * glow + 0.20 * text + 0.14 * dark
    raw["text_document"] = 0.62 * d["text_proxy"] + 0.24 * text + 0.14 * light

    # Surface / special.
    raw["texture_pattern"] = 0.56 * G["surface"] + 0.30 * tex + 0.14 * entropy
    raw["pattern_seamless"] = 0.46 * pattern + 0.28 * G["surface"] + 0.18 * flat + 0.08 * direction
    raw["retro_halftone"] = 0.55 * d["dot_halftone_proxy"] + 0.22 * pattern + 0.13 * gray + 0.10 * line
    raw["pixel_art"] = 0.66 * G["pixel"] + 0.22 * flat + 0.12 * (1.0 - entropy)
    raw["sprite_sheet"] = 0.32 * G["pixel"] + 0.30 * layout + 0.23 * flat + 0.15 * line
    raw["vfx_glow_magic"] = 0.68 * G["vfx"] + 0.20 * glow + 0.12 * dark
    raw["space_scifi_bg"] = 0.55 * G["space"] + 0.26 * dark + 0.19 * glow
    raw["transparent_layered"] = 0.74 * trans + 0.16 * flat + 0.10 * light
    raw["low_poly"] = 0.38 * flat + 0.28 * edge + 0.18 * (1.0 - tex) + 0.16 * sat
    raw["noisy_compressed"] = 0.70 * noisy + 0.18 * entropy + 0.12 * (1.0 - edge)

    # Hard suppressors: stop over-sticky wrong classes.
    if photo > 0.55 and entropy > 0.50:
        raw["pixel_art"] *= 0.35
        raw["vector_flat"] *= 0.65
    if tex > 0.55:
        raw["pixel_art"] *= 0.55
        raw["portrait_character"] *= 0.60
    if layout > 0.62:
        raw["portrait_character"] *= 0.45
        raw["photo_general"] *= 0.70
    if skin < 0.035:
        raw["portrait_character"] *= 0.45
    if G["pixel"] < 0.45:
        raw["pixel_art"] *= 0.55
    if trans > 0.10 or alpha > 0.08:
        raw["transparent_layered"] += 0.20
        raw["photo_general"] *= 0.75
    if gray > 0.78:
        raw["grayscale_ink"] += 0.08
        raw["lineart_sketch"] += 0.08
        raw["photo_general"] *= 0.85
    if text > 0.70:
        raw["text_document"] += 0.18
        raw["ui_screenshot"] += 0.05
    if glow > 0.65:
        raw["vfx_glow_magic"] += 0.15
    if line > 0.70 and light > 0.35:
        raw["lineart_sketch"] += 0.12
    if anime > 0.68:
        raw["anime_manga"] += 0.14

    # Aspect hints: sprite sheets / UI / documents often non-square/wide/tall.
    if aspect > 1.45 and layout > 0.45:
        raw["sprite_sheet"] += 0.04
        raw["diagram_chart"] += 0.03
    if aspect < 0.75 and layout > 0.45:
        raw["ui_screenshot"] += 0.04
        raw["text_document"] += 0.03

    return {c: clamp01(raw.get(c, 0.0)) for c in classes}


def normalize_confidences(scores: Dict[str, float]) -> List[Dict[str, Any]]:
    ordered = sorted(scores.items(), key=lambda kv: kv[1], reverse=True)
    total = sum(max(0.001, s) for _, s in ordered[:8])
    return [{"class": cls, "score": round(float(s), 5), "confidence": round(safe_div(max(0.001, s), total), 5)} for cls, s in ordered]


def analyze_image(path: Path, classes: List[str] | None = None) -> Dict[str, Any]:
    if classes is None:
        classes = DEFAULT_CLASSES
    f = v1.extract_features(path)
    d = derive_v2_features(f)
    ranked = normalize_confidences(score_classes_v2(f, classes))
    top3 = [r["class"] for r in ranked[:3]]
    top1 = top3[0] if top3 else "unknown"
    plan = CLASS_DRAW_PLANS.get(top1, {
        "draw_priority": ["main_subject", "large_shapes", "important_edges", "details"],
        "layer_order": ["background", "large_shapes", "objects", "details"],
    })
    tags = v1.tags_from_features(f)
    # Add v2 group tags.
    for k, val in d.items():
        if val > 0.62 and k not in tags:
            tags.append(k)
    return {
        "id": path.stem,
        "file": path.name,
        "top1": top1,
        "top3": top3,
        "ranked": ranked[:8],
        "confidence": ranked[0]["confidence"] if ranked else 0.0,
        "features": f,
        "v2_features": d,
        "feature_tags": tags[:16],
        "draw_priority": plan["draw_priority"],
        "layer_order": plan["layer_order"],
        "pixel_only_rule": "Analyzer used only bitmap pixels, not label/json/csv/source/prompt.",
        "analyzer_version": "v2_coarse_to_fine_rule_based",
    }


def load_labels(dataset: Path) -> Dict[str, Dict[str, Any]]:
    return v1.load_labels(dataset)


def find_image_for_id(images_dir: Path, image_id: str) -> Path | None:
    return v1.find_image_for_id(images_dir, image_id)


def classify_error(true_cls: str, pred: str, top3: List[str]) -> str:
    return v1.classify_error(true_cls, pred, top3)


def explain_error(true_cls: str, pred: str) -> str:
    return v1.explain_error(true_cls, pred)


def run_benchmark(dataset: Path, out: str | None = None, limit: int = 0) -> Path:
    dataset = dataset.expanduser().resolve()
    labels = load_labels(dataset)
    images_dir = dataset / "images"
    if not images_dir.is_dir():
        raise FileNotFoundError(f"missing images dir: {images_dir}")

    classes = sorted({d.get("true_class", "") for d in labels.values() if d.get("true_class")}) or DEFAULT_CLASSES
    run_dir = Path(out).expanduser() if out else dataset / "benchmark_runs" / f"run_v2_{_dt.datetime.now().strftime('%Y%m%d_%H%M%S')}"
    run_dir.mkdir(parents=True, exist_ok=True)

    results_csv = run_dir / "benchmark_results.csv"
    mistakes_csv = run_dir / "mistakes.csv"
    predictions_jsonl = run_dir / "predictions.jsonl"
    summary_json = run_dir / "benchmark_summary.json"
    report_md = run_dir / "BENCHMARK_REPORT.md"

    fields = ["id", "file", "true_class", "top1", "top2", "top3", "conf1", "ok_top1", "ok_top3", "error_type", "feature_tags", "draw_priority", "layer_order", "notes"]
    rows, mistake_rows, predictions = [], [], []
    per_class = defaultdict(lambda: {"total": 0, "top1": 0, "top3": 0})
    confusions = Counter()

    ids = sorted(labels.keys())
    if limit > 0:
        ids = ids[:limit]

    for idx, image_id in enumerate(ids, 1):
        label = labels[image_id]
        img_path = find_image_for_id(images_dir, image_id)
        if img_path is None:
            print(f"WARN: missing image for id {image_id}", file=sys.stderr)
            continue

        true_cls = label.get("true_class", "")
        pred = analyze_image(img_path, classes=classes)
        top3, top1 = pred["top3"], pred["top1"]
        ok1, ok3 = top1 == true_cls, true_cls in top3

        per_class[true_cls]["total"] += 1
        per_class[true_cls]["top1"] += int(ok1)
        per_class[true_cls]["top3"] += int(ok3)
        if not ok1:
            confusions[(true_cls, top1)] += 1

        row = {
            "id": image_id,
            "file": pred["file"],
            "true_class": true_cls,
            "top1": top1,
            "top2": top3[1] if len(top3) > 1 else "",
            "top3": top3[2] if len(top3) > 2 else "",
            "conf1": pred["confidence"],
            "ok_top1": str(ok1).lower(),
            "ok_top3": str(ok3).lower(),
            "error_type": classify_error(true_cls, top1, top3),
            "feature_tags": "|".join(pred["feature_tags"]),
            "draw_priority": "|".join(pred["draw_priority"]),
            "layer_order": "|".join(pred["layer_order"]),
            "notes": explain_error(true_cls, top1),
        }
        rows.append(row)
        if not ok1:
            mistake_rows.append(row)

        predictions.append({
            "id": image_id,
            "file": pred["file"],
            "true_class": true_cls,
            "top1": top1,
            "top3": top3,
            "confidence": pred["confidence"],
            "ok_top1": ok1,
            "ok_top3": ok3,
            "feature_tags": pred["feature_tags"],
            "features": pred["features"],
            "v2_features": pred["v2_features"],
            "draw_priority": pred["draw_priority"],
            "layer_order": pred["layer_order"],
            "ranked": pred["ranked"],
            "analyzer_version": pred["analyzer_version"],
        })

        if idx % 25 == 0:
            print(f"processed {idx}/{len(ids)}")

    for path, data_rows in [(results_csv, rows), (mistakes_csv, mistake_rows)]:
        with path.open("w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=fields)
            w.writeheader()
            w.writerows(data_rows)

    with predictions_jsonl.open("w", encoding="utf-8") as f:
        for p in predictions:
            f.write(json.dumps(p, ensure_ascii=False, separators=(",", ":")) + "\n")

    total = len(rows)
    top1_total = sum(1 for r in rows if r["ok_top1"] == "true")
    top3_total = sum(1 for r in rows if r["ok_top3"] == "true")

    per_class_summary = {}
    for cls, s in sorted(per_class.items()):
        t = s["total"]
        per_class_summary[cls] = {
            "total": t,
            "top1_correct": s["top1"],
            "top3_correct": s["top3"],
            "top1_accuracy": round(safe_div(s["top1"], t), 5),
            "top3_accuracy": round(safe_div(s["top3"], t), 5),
        }

    common_confusions = [{"true": a, "pred": b, "count": c} for (a, b), c in confusions.most_common(30)]
    worst_classes = sorted(per_class_summary.keys(), key=lambda c: (per_class_summary[c]["top1_accuracy"], -per_class_summary[c]["total"]))[:10]

    # Compare to latest v1 run if user has it, optional manual inspection only.
    summary = {
        "analyzer_version": "v2_coarse_to_fine_rule_based",
        "dataset": str(dataset),
        "run_dir": str(run_dir),
        "total": total,
        "top1_correct": top1_total,
        "top3_correct": top3_total,
        "top1_accuracy": round(safe_div(top1_total, total), 5),
        "top3_accuracy": round(safe_div(top3_total, total), 5),
        "classes": classes,
        "per_class": per_class_summary,
        "worst_classes": worst_classes,
        "common_confusions": common_confusions,
        "outputs": {
            "benchmark_results_csv": str(results_csv),
            "mistakes_csv": str(mistakes_csv),
            "predictions_jsonl": str(predictions_jsonl),
            "benchmark_summary_json": str(summary_json),
            "report_md": str(report_md),
        },
        "note": "Analyzer predictions used image pixels only. Labels were used only by benchmark comparison.",
    }
    summary_json.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# SolumDraw Benchmark Report v2",
        "",
        f"- Analyzer: `v2_coarse_to_fine_rule_based`",
        f"- Dataset: `{dataset}`",
        f"- Total images: `{total}`",
        f"- Top-1 accuracy: `{summary['top1_accuracy']}`",
        f"- Top-3 accuracy: `{summary['top3_accuracy']}`",
        f"- Mistakes: `{len(mistake_rows)}`",
        "",
        "## Worst classes",
    ]
    for cls in worst_classes:
        s = per_class_summary[cls]
        lines.append(f"- `{cls}`: top1 `{s['top1_accuracy']}` / top3 `{s['top3_accuracy']}` / total `{s['total']}`")
    lines.append("")
    lines.append("## Common confusions")
    for c in common_confusions[:15]:
        lines.append(f"- `{c['true']}` -> `{c['pred']}`: `{c['count']}`")
    lines.append("")
    lines.append("## Output files")
    for k, v in summary["outputs"].items():
        lines.append(f"- `{k}`: `{v}`")
    report_md.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("=== BENCHMARK V2 DONE ===")
    print("dataset:", dataset)
    print("run_dir:", run_dir)
    print("total:", total)
    print("top1_accuracy:", summary["top1_accuracy"])
    print("top3_accuracy:", summary["top3_accuracy"])
    print("results_csv:", results_csv)
    print("mistakes_csv:", mistakes_csv)
    print("summary_json:", summary_json)
    return run_dir


def run_single(image: Path, out: str | None = None, html: bool = False) -> Path:
    image = image.expanduser().resolve()
    out_dir = Path(out).expanduser() if out else Path.cwd() / f"single_analysis_v2_{_dt.datetime.now().strftime('%Y%m%d_%H%M%S')}"
    out_dir.mkdir(parents=True, exist_ok=True)
    analysis = analyze_image(image, classes=DEFAULT_CLASSES)
    analysis["source_image"] = str(image)
    out_json = out_dir / "single_analysis.json"
    out_json.write_text(json.dumps(analysis, ensure_ascii=False, indent=2), encoding="utf-8")
    if html:
        # Reuse v1 renderer; it ignores v2-only fields but shows top/classes/features.
        v1.render_single_html(image, analysis, out_dir / "single_analysis.html")
    print("=== SINGLE ANALYSIS V2 DONE ===")
    print("image:", image)
    print("top1:", analysis["top1"])
    print("top3:", ", ".join(analysis["top3"]))
    print("json:", out_json)
    if html:
        print("html:", out_dir / "single_analysis.html")
    return out_dir


def main() -> None:
    ap = argparse.ArgumentParser(description="SolumDraw Analyzer + Benchmark v2")
    sub = ap.add_subparsers(dest="cmd", required=True)

    b = sub.add_parser("benchmark", help="Run dataset benchmark")
    b.add_argument("--dataset", default=str(Path.home() / "SolumDraw/datasets/SolumDrawDataset_v1"))
    b.add_argument("--out", default="")
    b.add_argument("--limit", type=int, default=0)

    s = sub.add_parser("single", help="Analyze one image")
    s.add_argument("--image", required=True)
    s.add_argument("--out", default="")
    s.add_argument("--html", action="store_true")

    args = ap.parse_args()
    if args.cmd == "benchmark":
        run_benchmark(Path(args.dataset), args.out or None, args.limit)
    elif args.cmd == "single":
        run_single(Path(args.image), args.out or None, args.html)


if __name__ == "__main__":
    main()
