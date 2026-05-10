#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SolumDraw Analyzer + Benchmark v1

Goal:
- Analyze image pixels only.
- Predict genre/type top-1/top-3.
- Extract drawing-relevant features.
- Produce lightweight benchmark reports for the SolumDraw dataset.

Batch benchmark outputs:
  benchmark_results.csv   — compact table
  predictions.jsonl       — one JSON per image
  mistakes.csv            — only errors
  benchmark_summary.json  — global/per-class/common-confusions summary
  BENCHMARK_REPORT.md     — readable short report

Single image outputs:
  single_analysis.json
  single_analysis.html, optional visual report

Install dependency if missing:
  pip install pillow
"""

from __future__ import annotations

import argparse
import base64
import csv
import datetime as _dt
import json
import math
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, Iterable, List, Tuple

try:
    from PIL import Image
except Exception:
    print("ERROR: Pillow is required. Install: pip install pillow", file=sys.stderr)
    raise


DEFAULT_CLASSES = [
    "abstract_art", "animal_creature", "anime_manga", "architecture_hardsurface",
    "cartoon_comic", "diagram_chart", "digital_painting_concept", "game_ui_hud",
    "grayscale_ink", "human_body_fullbody", "ink_wash", "isometric_art",
    "landscape_environment", "lineart_sketch", "logo_icon", "low_poly",
    "noisy_compressed", "oil_painting", "pattern_seamless", "pencil_drawing",
    "photo_general", "pixel_art", "portrait_character", "product_object",
    "retro_halftone", "space_scifi_bg", "sprite_sheet", "sticker_chibi",
    "still_life", "text_document", "texture_pattern", "transparent_layered",
    "ui_screenshot", "vector_flat", "vfx_glow_magic", "watercolor_paint",
]


CLASS_DRAW_PLANS: Dict[str, Dict[str, List[str]]] = {
    "anime_manga": {
        "draw_priority": ["face", "eyes", "hair", "main_lineart", "flat_or_dark_masses", "hatching_or_highlights"],
        "layer_order": ["background_or_paper", "rough_silhouette", "main_lineart", "shadow_masses", "face_details", "final_accents"],
    },
    "lineart_sketch": {
        "draw_priority": ["main_contours", "face_or_main_object", "secondary_lines", "hatching", "tiny_marks"],
        "layer_order": ["white_background", "primary_contours", "secondary_contours", "hatching", "cleanup"],
    },
    "pencil_drawing": {
        "draw_priority": ["main_contours", "value_blocks", "hatching_direction", "dark_accents", "paper_texture"],
        "layer_order": ["paper", "light_construction", "mid_values", "hatching", "dark_accents"],
    },
    "grayscale_ink": {
        "draw_priority": ["darkest_masses", "main_silhouette", "midtone_wash", "line_details"],
        "layer_order": ["white_space", "gray_washes", "black_masses", "line_details"],
    },
    "ink_wash": {
        "draw_priority": ["main_brush_gesture", "large_washes", "dark_ink_shapes", "calligraphy_or_seal"],
        "layer_order": ["paper_white", "soft_washes", "dark_brush_shapes", "details"],
    },
    "watercolor_paint": {
        "draw_priority": ["large_washes", "soft_edges", "mid_color_regions", "dark_accents", "fine_lines"],
        "layer_order": ["paper", "light_washes", "mid_washes", "dark_accents", "fine_details"],
    },
    "oil_painting": {
        "draw_priority": ["large_value_masses", "main_objects", "brush_direction", "thick_highlights"],
        "layer_order": ["underpainting", "large_paint_masses", "surface_strokes", "highlights"],
    },
    "photo_general": {
        "draw_priority": ["main_subject", "large_light_shadow_masses", "important_edges", "texture_regions", "small_details"],
        "layer_order": ["background", "large_values", "main_objects", "textures", "details"],
    },
    "portrait_character": {
        "draw_priority": ["face_outline", "eyes", "nose_mouth", "hair_mass", "skin_values", "clothes"],
        "layer_order": ["background", "head_silhouette", "skin_base", "hair_and_clothes", "facial_features", "micro_details"],
    },
    "human_body_fullbody": {
        "draw_priority": ["pose_silhouette", "head", "torso", "limbs", "clothing_edges", "details"],
        "layer_order": ["background", "body_silhouette", "large_fills", "clothing", "lineart", "details"],
    },
    "animal_creature": {
        "draw_priority": ["head", "eyes", "body_outline", "fur_or_skin_texture", "habitat"],
        "layer_order": ["background", "animal_silhouette", "body_values", "head_features", "surface_texture"],
    },
    "vector_flat": {
        "draw_priority": ["largest_flat_shapes", "closed_shape_edges", "inner_shapes", "small_icons"],
        "layer_order": ["background_shape", "large_flat_shapes", "inner_shapes", "accents"],
    },
    "logo_icon": {
        "draw_priority": ["symbol_outline", "negative_space", "internal_shapes", "typography_if_present"],
        "layer_order": ["background", "main_symbol", "internal_cutouts", "text", "edge_cleanup"],
    },
    "ui_screenshot": {
        "draw_priority": ["layout_boxes", "panels_cards", "text_blocks", "buttons_icons", "separators"],
        "layer_order": ["background", "panels", "cards", "buttons", "icons", "text"],
    },
    "game_ui_hud": {
        "draw_priority": ["hud_frames", "bars", "icons", "numbers_text", "glow"],
        "layer_order": ["viewport", "hud_panels", "bars", "icons", "text", "glow"],
    },
    "text_document": {
        "draw_priority": ["large_text", "text_blocks", "baselines", "small_text", "decor"],
        "layer_order": ["background", "text_regions", "large_text", "small_text", "decor"],
    },
    "diagram_chart": {
        "draw_priority": ["layout_structure", "boxes_axes", "connectors_arrows", "labels", "icons"],
        "layer_order": ["background", "grid_or_axes", "shapes", "connectors", "text"],
    },
    "pixel_art": {
        "draw_priority": ["pixel_blocks", "outline_pixels", "palette_clusters", "highlight_pixels"],
        "layer_order": ["pixel_blocks", "outline", "shadow_blocks", "highlight_pixels"],
    },
    "sprite_sheet": {
        "draw_priority": ["cell_boundaries", "per_frame_silhouette", "frame_consistency", "details"],
        "layer_order": ["grid_detection", "per_frame_silhouette", "per_frame_fills", "per_frame_details"],
    },
    "texture_pattern": {
        "draw_priority": ["large_repeats", "material_direction", "cracks_or_grain", "ignore_micro_noise"],
        "layer_order": ["base_color", "large_pattern", "medium_texture", "detail_noise"],
    },
    "pattern_seamless": {
        "draw_priority": ["repeat_unit", "motif_edges", "symmetry", "color_accents"],
        "layer_order": ["base", "repeat_grid", "motifs", "accents"],
    },
    "vfx_glow_magic": {
        "draw_priority": ["bright_core", "glow_radius", "particle_clusters", "dark_negative_space"],
        "layer_order": ["base_background", "soft_glow", "bright_core", "particles", "spark_highlights"],
    },
    "transparent_layered": {
        "draw_priority": ["alpha_boundary", "main_cutout", "internal_edges", "cleanup"],
        "layer_order": ["alpha_mask", "base_shape", "internal_color", "details", "edge_cleanup"],
    },
    "landscape_environment": {
        "draw_priority": ["horizon", "sky", "large_depth_planes", "foreground_silhouettes", "small_details"],
        "layer_order": ["sky", "far_background", "midground", "foreground", "details"],
    },
    "architecture_hardsurface": {
        "draw_priority": ["perspective_lines", "large_planes", "structural_edges", "materials", "details"],
        "layer_order": ["background", "large_planes", "structural_edges", "materials", "details"],
    },
    "product_object": {
        "draw_priority": ["object_outline", "main_material", "brand_label", "reflections", "shadow"],
        "layer_order": ["clean_background", "object_silhouette", "material_base", "labels", "highlights"],
    },
    "still_life": {
        "draw_priority": ["object_overlaps", "main_object", "table_shadow", "materials", "highlights"],
        "layer_order": ["background", "table", "back_objects", "front_objects", "details"],
    },
    "abstract_art": {
        "draw_priority": ["largest_shapes", "color_boundaries", "rhythm", "texture", "accents"],
        "layer_order": ["background_color", "large_shapes", "overlaps", "texture", "accents"],
    },
    "digital_painting_concept": {
        "draw_priority": ["focal_point", "big_shapes", "light_path", "silhouettes", "details"],
        "layer_order": ["sky_or_background", "large_values", "atmosphere", "main_silhouettes", "focal_details"],
    },
    "space_scifi_bg": {
        "draw_priority": ["dark_space", "planets_or_nebula", "bright_glow", "stars", "particles"],
        "layer_order": ["dark_space", "nebula_glow", "planets", "stars", "light_accents"],
    },
    "low_poly": {
        "draw_priority": ["polygon_edges", "large_facets", "facet_shadows", "highlights"],
        "layer_order": ["background", "large_facets", "facet_shadows", "edge_accents"],
    },
    "retro_halftone": {
        "draw_priority": ["large_color_fields", "dot_clusters", "printed_edges", "linework"],
        "layer_order": ["base_color", "dot_pattern", "linework", "text"],
    },
    "noisy_compressed": {
        "draw_priority": ["large_shapes", "high_confidence_edges", "important_subject", "skip_noise"],
        "layer_order": ["large_blurry_masses", "strong_edges", "important_subject", "ignore_artifacts"],
    },
}


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


def image_to_small_rgba(path: Path, max_side: int = 160) -> Image.Image:
    img = Image.open(path)
    img.load()
    img = img.convert("RGBA")
    w, h = img.size
    scale = min(1.0, max_side / max(w, h))
    if scale < 1.0:
        img = img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.Resampling.BILINEAR)
    return img


def luminance(r: int, g: int, b: int) -> float:
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def rgb_to_hsv_simple(r: int, g: int, b: int) -> Tuple[float, float, float]:
    rf, gf, bf = r / 255.0, g / 255.0, b / 255.0
    mx, mn = max(rf, gf, bf), min(rf, gf, bf)
    d = mx - mn
    if d == 0:
        h = 0.0
    elif mx == rf:
        h = ((gf - bf) / d) % 6.0
    elif mx == gf:
        h = (bf - rf) / d + 2.0
    else:
        h = (rf - gf) / d + 4.0
    h /= 6.0
    s = 0.0 if mx == 0 else d / mx
    return h, s, mx


def quant_key(r: int, g: int, b: int, bins: int = 8) -> Tuple[int, int, int]:
    step = 256 // bins
    return (r // step, g // step, b // step)


def extract_features(path: Path) -> Dict[str, Any]:
    img = image_to_small_rgba(path)
    w, h = img.size
    pix = list(img.getdata())
    n = max(1, len(pix))

    lum: List[float] = []
    sat: List[float] = []
    val: List[float] = []
    hue_bins = [0] * 12
    qcolors = Counter()
    alpha_non_opaque = 0
    alpha_zero = 0
    skin_like = 0
    dark = 0
    light = 0
    colored = 0

    for r, g, b, a in pix:
        y = luminance(r, g, b)
        lum.append(y)
        hh, ss, vv = rgb_to_hsv_simple(r, g, b)
        sat.append(ss)
        val.append(vv)
        if ss > 0.16:
            hue_bins[int(hh * 12) % 12] += 1
            colored += 1
        qcolors[quant_key(r, g, b, 8)] += 1
        if a < 250:
            alpha_non_opaque += 1
        if a < 10:
            alpha_zero += 1
        if r > 70 and g > 35 and b > 20 and r > g and g >= b and (r - b) > 25 and ss > 0.15:
            skin_like += 1
        if y < 40:
            dark += 1
        if y > 220:
            light += 1

    mean_lum = statistics.fmean(lum)
    stdev_lum = statistics.pstdev(lum) if len(lum) > 1 else 0.0
    mean_sat = statistics.fmean(sat)
    mean_val = statistics.fmean(val)

    entropy = 0.0
    for c in qcolors.values():
        p = c / n
        entropy -= p * math.log(p + 1e-12, 2)
    entropy_norm = clamp01(entropy / 9.0)

    top_colors = sum(c for _, c in qcolors.most_common(12))
    top_color_ratio = top_colors / n

    gray = [int(v) for v in lum]
    edge_values: List[float] = []
    strong_edges = 0
    horizontal_edges = 0
    vertical_edges = 0
    for y in range(1, h - 1):
        row = y * w
        for x in range(1, w - 1):
            gx = abs(gray[row + x + 1] - gray[row + x - 1])
            gy = abs(gray[row + w + x] - gray[row - w + x])
            e = gx + gy
            edge_values.append(e)
            if e > 80:
                strong_edges += 1
                if gx > gy * 1.35:
                    vertical_edges += 1
                elif gy > gx * 1.35:
                    horizontal_edges += 1

    edge_mean = safe_div(sum(edge_values), len(edge_values), 0.0)
    edge_density = safe_div(strong_edges, max(1, len(edge_values)), 0.0)
    line_direction_bias = safe_div(abs(vertical_edges - horizontal_edges), max(1, strong_edges), 0.0)

    diffs = []
    for y in range(0, h - 1):
        for x in range(0, w - 1):
            i = y * w + x
            diffs.append(abs(gray[i] - gray[i + 1]))
            diffs.append(abs(gray[i] - gray[i + w]))
    diff_mean = safe_div(sum(diffs), len(diffs), 0.0)
    diff_var = statistics.pstdev(diffs) if len(diffs) > 1 else 0.0

    grayscale_pixels = 0
    for r, g, b, a in pix:
        if max(abs(r - g), abs(g - b), abs(r - b)) < 12:
            grayscale_pixels += 1
    grayscale_ratio = grayscale_pixels / n

    flat_score = clamp01(0.55 * top_color_ratio + 0.35 * (1.0 - entropy_norm) + 0.10 * sigmoid01(edge_density, 0.08, 0.04))
    lineart_score = clamp01(0.34 * sigmoid01(edge_density, 0.10, 0.05) + 0.28 * (light / n) + 0.22 * grayscale_ratio + 0.16 * sigmoid01(stdev_lum, 50, 18))
    photo_score = clamp01(0.30 * entropy_norm + 0.25 * sigmoid01(stdev_lum, 45, 18) + 0.20 * (1.0 - flat_score) + 0.15 * (1.0 - grayscale_ratio) + 0.10 * (1.0 - alpha_non_opaque / n))
    layout_score = clamp01(0.35 * sigmoid01(edge_density, 0.12, 0.05) + 0.25 * line_direction_bias + 0.20 * flat_score + 0.20 * sigmoid01(light / n, 0.25, 0.15))
    texture_score = clamp01(0.35 * entropy_norm + 0.30 * sigmoid01(diff_mean, 18, 10) + 0.20 * sigmoid01(edge_density, 0.10, 0.06) + 0.15 * (1.0 - line_direction_bias))
    hue_dominance = sigmoid01(max(hue_bins) / max(1, colored), 0.35, 0.10) if colored else 0.0
    glow_score = clamp01(0.35 * sigmoid01(mean_sat, 0.35, 0.12) + 0.25 * sigmoid01(light / n, 0.08, 0.05) + 0.25 * sigmoid01(dark / n, 0.25, 0.15) + 0.15 * hue_dominance)
    pixel_score = clamp01(0.45 * (1.0 - entropy_norm) + 0.30 * top_color_ratio + 0.25 * sigmoid01(edge_density, 0.15, 0.06))
    transparent_score = clamp01(0.75 * (alpha_non_opaque / n) + 0.25 * (alpha_zero / n))
    skin_ratio = skin_like / n
    face_like_score = clamp01(0.45 * sigmoid01(skin_ratio, 0.06, 0.04) + 0.25 * sigmoid01(edge_density, 0.08, 0.05) + 0.20 * sigmoid01(mean_lum, 80, 35) + 0.10 * (1.0 - transparent_score))
    anime_score = clamp01(0.38 * lineart_score + 0.22 * face_like_score + 0.22 * flat_score + 0.18 * grayscale_ratio)
    text_score = clamp01(0.45 * layout_score + 0.30 * (light / n) + 0.25 * grayscale_ratio)
    pattern_score = clamp01(0.42 * texture_score + 0.28 * flat_score + 0.20 * (1.0 - photo_score) + 0.10 * line_direction_bias)
    noisy_score = clamp01(0.45 * sigmoid01(diff_var, 28, 12) + 0.25 * sigmoid01(diff_mean, 12, 8) + 0.20 * (1.0 - edge_density) + 0.10 * entropy_norm)

    return {
        "width": w, "height": h, "aspect": round(w / max(1, h), 4),
        "mean_luminance": round(mean_lum, 3), "luminance_stdev": round(stdev_lum, 3),
        "mean_saturation": round(mean_sat, 4), "mean_value": round(mean_val, 4),
        "entropy_norm": round(entropy_norm, 4), "top_color_ratio": round(top_color_ratio, 4),
        "edge_density": round(edge_density, 4), "edge_mean": round(edge_mean, 3),
        "line_direction_bias": round(line_direction_bias, 4), "grayscale_ratio": round(grayscale_ratio, 4),
        "light_ratio": round(light / n, 4), "dark_ratio": round(dark / n, 4),
        "skin_like_ratio": round(skin_ratio, 4), "alpha_non_opaque_ratio": round(alpha_non_opaque / n, 4),
        "alpha_zero_ratio": round(alpha_zero / n, 4), "flat_score": round(flat_score, 4),
        "lineart_score": round(lineart_score, 4), "photo_score": round(photo_score, 4),
        "layout_score": round(layout_score, 4), "texture_score": round(texture_score, 4),
        "glow_score": round(glow_score, 4), "pixel_score": round(pixel_score, 4),
        "transparent_score": round(transparent_score, 4), "face_like_score": round(face_like_score, 4),
        "anime_score": round(anime_score, 4), "text_score": round(text_score, 4),
        "pattern_score": round(pattern_score, 4), "noisy_score": round(noisy_score, 4),
    }


def tags_from_features(f: Dict[str, Any]) -> List[str]:
    checks = [
        ("photo_texture", f["photo_score"] > 0.55), ("lineart", f["lineart_score"] > 0.55),
        ("flat_fill", f["flat_score"] > 0.55), ("grayscale", f["grayscale_ratio"] > 0.70),
        ("transparent", f["transparent_score"] > 0.08), ("texture", f["texture_score"] > 0.58),
        ("glow", f["glow_score"] > 0.55), ("pixel_grid", f["pixel_score"] > 0.62),
        ("ui_layout", f["layout_score"] > 0.62), ("text_dominant", f["text_score"] > 0.65),
        ("face_priority", f["face_like_score"] > 0.55), ("anime_manga_style", f["anime_score"] > 0.58),
        ("noise_or_compression", f["noisy_score"] > 0.62),
    ]
    return [name for name, ok in checks if ok][:12]


def score_classes(f: Dict[str, Any], classes: Iterable[str]) -> Dict[str, float]:
    flat, line, photo, layout = f["flat_score"], f["lineart_score"], f["photo_score"], f["layout_score"]
    tex, glow, pixel, trans = f["texture_score"], f["glow_score"], f["pixel_score"], f["transparent_score"]
    face, anime, text, pattern, noisy = f["face_like_score"], f["anime_score"], f["text_score"], f["pattern_score"], f["noisy_score"]
    gray, sat, edge, dark, light, entropy = f["grayscale_ratio"], f["mean_saturation"], f["edge_density"], f["dark_ratio"], f["light_ratio"], f["entropy_norm"]

    raw = {
        "photo_general": 0.70 * photo + 0.10 * face + 0.10 * tex + 0.10 * (1 - flat),
        "portrait_character": 0.55 * face + 0.20 * photo + 0.15 * anime + 0.10 * line,
        "human_body_fullbody": 0.35 * face + 0.25 * anime + 0.20 * line + 0.20 * photo,
        "animal_creature": 0.45 * photo + 0.25 * tex + 0.15 * face + 0.15 * (1 - layout),
        "anime_manga": 0.55 * anime + 0.22 * line + 0.13 * face + 0.10 * gray,
        "sticker_chibi": 0.36 * anime + 0.32 * flat + 0.18 * face + 0.14 * light,
        "cartoon_comic": 0.32 * line + 0.28 * flat + 0.20 * text + 0.20 * layout,
        "lineart_sketch": 0.68 * line + 0.20 * gray + 0.12 * light,
        "pencil_drawing": 0.42 * line + 0.26 * gray + 0.20 * tex + 0.12 * (1 - flat),
        "grayscale_ink": 0.38 * gray + 0.30 * line + 0.20 * dark + 0.12 * tex,
        "ink_wash": 0.35 * gray + 0.24 * line + 0.20 * light + 0.21 * (1 - entropy),
        "watercolor_paint": 0.28 * tex + 0.25 * (1 - edge) + 0.22 * sat + 0.15 * light + 0.10 * photo,
        "oil_painting": 0.38 * tex + 0.25 * entropy + 0.20 * sat + 0.17 * photo,
        "digital_painting_concept": 0.32 * tex + 0.25 * sat + 0.22 * glow + 0.21 * entropy,
        "low_poly": 0.42 * flat + 0.26 * edge + 0.20 * (1 - tex) + 0.12 * sat,
        "pixel_art": 0.62 * pixel + 0.25 * flat + 0.13 * (1 - entropy),
        "sprite_sheet": 0.35 * pixel + 0.28 * layout + 0.22 * flat + 0.15 * line,
        "vector_flat": 0.62 * flat + 0.20 * layout + 0.18 * (1 - tex),
        "logo_icon": 0.42 * flat + 0.26 * layout + 0.18 * light + 0.14 * line,
        "isometric_art": 0.46 * flat + 0.22 * layout + 0.18 * edge + 0.14 * sat,
        "retro_halftone": 0.35 * pattern + 0.28 * tex + 0.20 * line + 0.17 * gray,
        "landscape_environment": 0.35 * photo + 0.25 * entropy + 0.18 * tex + 0.12 * sat + 0.10 * (1 - face),
        "space_scifi_bg": 0.42 * dark + 0.30 * glow + 0.16 * sat + 0.12 * entropy,
        "architecture_hardsurface": 0.38 * layout + 0.25 * photo + 0.22 * edge + 0.15 * gray,
        "product_object": 0.35 * photo + 0.28 * light + 0.20 * flat + 0.17 * face,
        "still_life": 0.38 * photo + 0.30 * tex + 0.18 * entropy + 0.14 * sat,
        "texture_pattern": 0.66 * tex + 0.22 * pattern + 0.12 * entropy,
        "pattern_seamless": 0.55 * pattern + 0.25 * flat + 0.20 * tex,
        "diagram_chart": 0.56 * layout + 0.25 * text + 0.19 * flat,
        "ui_screenshot": 0.60 * layout + 0.22 * text + 0.18 * flat,
        "game_ui_hud": 0.45 * layout + 0.26 * glow + 0.18 * text + 0.11 * dark,
        "text_document": 0.66 * text + 0.20 * layout + 0.14 * light,
        "vfx_glow_magic": 0.70 * glow + 0.20 * dark + 0.10 * sat,
        "transparent_layered": 0.72 * trans + 0.18 * flat + 0.10 * light,
        "abstract_art": 0.38 * entropy + 0.28 * sat + 0.20 * tex + 0.14 * (1 - face),
        "noisy_compressed": 0.70 * noisy + 0.18 * entropy + 0.12 * (1 - edge),
    }

    if trans > 0.12:
        raw["transparent_layered"] += 0.25; raw["photo_general"] *= 0.82
    if gray > 0.80:
        raw["grayscale_ink"] += 0.15; raw["lineart_sketch"] += 0.10; raw["photo_general"] *= 0.82
    if text > 0.70:
        raw["text_document"] += 0.18; raw["photo_general"] *= 0.75
    if glow > 0.65:
        raw["vfx_glow_magic"] += 0.18
    if pixel > 0.72:
        raw["pixel_art"] += 0.18
    if line > 0.70 and light > 0.35:
        raw["lineart_sketch"] += 0.14
    if anime > 0.68:
        raw["anime_manga"] += 0.16

    return {c: clamp01(raw.get(c, 0.0)) for c in classes}


def normalize_confidences(scores: Dict[str, float]) -> List[Dict[str, Any]]:
    ordered = sorted(scores.items(), key=lambda kv: kv[1], reverse=True)
    total = sum(max(0.001, s) for _, s in ordered[:8])
    return [{"class": cls, "score": round(float(s), 5), "confidence": round(safe_div(max(0.001, s), total), 5)} for cls, s in ordered]


def analyze_image(path: Path, classes: Iterable[str] = DEFAULT_CLASSES) -> Dict[str, Any]:
    f = extract_features(path)
    ranked = normalize_confidences(score_classes(f, classes))
    top3 = [r["class"] for r in ranked[:3]]
    top1 = top3[0] if top3 else "unknown"
    plan = CLASS_DRAW_PLANS.get(top1, {"draw_priority": ["main_subject", "large_shapes", "important_edges", "details"], "layer_order": ["background", "large_shapes", "objects", "details"]})
    return {
        "id": path.stem, "file": path.name, "top1": top1, "top3": top3,
        "ranked": ranked[:8], "confidence": ranked[0]["confidence"] if ranked else 0.0,
        "features": f, "feature_tags": tags_from_features(f),
        "draw_priority": plan["draw_priority"], "layer_order": plan["layer_order"],
        "pixel_only_rule": "Analyzer used only bitmap pixels, not label/json/csv/source/prompt.",
    }


def load_labels(dataset: Path) -> Dict[str, Dict[str, Any]]:
    labels_dir = dataset / "labels"
    if not labels_dir.is_dir():
        raise FileNotFoundError(f"missing labels dir: {labels_dir}")
    out = {}
    for p in sorted(labels_dir.glob("*.json")):
        try:
            d = json.loads(p.read_text(encoding="utf-8"))
            out[d["id"]] = d
        except Exception as e:
            print(f"WARN: cannot read label {p}: {e}", file=sys.stderr)
    return out


def find_image_for_id(images_dir: Path, image_id: str) -> Path | None:
    exts = {".png", ".jpg", ".jpeg", ".webp", ".bmp"}
    matches = [p for p in images_dir.glob(f"{image_id}.*") if p.suffix.lower() in exts]
    return sorted(matches)[0] if matches else None


def classify_error(true_cls: str, pred: str, top3: List[str]) -> str:
    if pred == true_cls:
        return ""
    if true_cls in top3:
        return "rank_order_error"
    near = {
        ("anime_manga", "lineart_sketch"): "style_vs_lineart_confusion",
        ("anime_manga", "pencil_drawing"): "style_vs_medium_confusion",
        ("lineart_sketch", "anime_manga"): "lineart_vs_anime_confusion",
        ("pencil_drawing", "lineart_sketch"): "medium_vs_lineart_confusion",
        ("ui_screenshot", "diagram_chart"): "layout_ui_vs_diagram_confusion",
        ("diagram_chart", "ui_screenshot"): "layout_diagram_vs_ui_confusion",
        ("texture_pattern", "pattern_seamless"): "texture_vs_repeat_confusion",
        ("pattern_seamless", "texture_pattern"): "repeat_vs_texture_confusion",
        ("vfx_glow_magic", "space_scifi_bg"): "glow_vs_space_confusion",
        ("space_scifi_bg", "vfx_glow_magic"): "space_vs_glow_confusion",
        ("vector_flat", "logo_icon"): "flat_vs_symbol_confusion",
        ("logo_icon", "vector_flat"): "symbol_vs_flat_confusion",
        ("photo_general", "portrait_character"): "photo_vs_face_priority_confusion",
        ("portrait_character", "photo_general"): "face_priority_too_weak",
    }
    return near.get((true_cls, pred), "wrong_class")


def explain_error(true_cls: str, pred: str) -> str:
    if pred == true_cls:
        return ""
    if true_cls == "anime_manga" and pred in ("lineart_sketch", "pencil_drawing"):
        return "Analyzer saw lines/medium but underweighted anime-specific face/eyes/hair structure."
    if true_cls in ("ui_screenshot", "diagram_chart") and pred in ("ui_screenshot", "diagram_chart"):
        return "Both classes share boxes/text/layout; need stronger button/card vs arrow/axis/node detection."
    if true_cls in ("texture_pattern", "pattern_seamless") and pred in ("texture_pattern", "pattern_seamless"):
        return "Texture and repeat pattern are close; need repeat-unit detection."
    if true_cls == "vfx_glow_magic" and pred == "space_scifi_bg":
        return "Glow and dark background dominated; need distinguish particles/effect overlays from cosmic background."
    if true_cls == "portrait_character" and pred == "photo_general":
        return "Face/portrait priority was too weak compared to generic photo score."
    if true_cls == "logo_icon" and pred == "vector_flat":
        return "Central symbol/logo structure was weaker than general flat-vector score."
    return "Feature scoring mismatch; inspect feature_tags and ranked scores."


def ensure_run_dir(dataset: Path, out: str | None) -> Path:
    p = Path(out).expanduser() if out else dataset / "benchmark_runs" / f"run_{_dt.datetime.now().strftime('%Y%m%d_%H%M%S')}"
    p.mkdir(parents=True, exist_ok=True)
    return p


def run_benchmark(dataset: Path, out: str | None = None, limit: int = 0) -> Path:
    dataset = dataset.expanduser().resolve()
    labels = load_labels(dataset)
    images_dir = dataset / "images"
    if not images_dir.is_dir():
        raise FileNotFoundError(f"missing images dir: {images_dir}")

    classes = sorted({d.get("true_class", "") for d in labels.values() if d.get("true_class")}) or DEFAULT_CLASSES
    run_dir = ensure_run_dir(dataset, out)
    results_csv, mistakes_csv = run_dir / "benchmark_results.csv", run_dir / "mistakes.csv"
    predictions_jsonl, summary_json, report_md = run_dir / "predictions.jsonl", run_dir / "benchmark_summary.json", run_dir / "BENCHMARK_REPORT.md"

    fields = ["id", "file", "true_class", "top1", "top2", "top3", "conf1", "ok_top1", "ok_top3", "error_type", "feature_tags", "draw_priority", "layer_order", "notes"]
    rows, mistake_rows, predictions = [], [], []
    per_class = defaultdict(lambda: {"total": 0, "top1": 0, "top3": 0})
    confusions = Counter()

    ids = sorted(labels.keys())
    if limit > 0:
        ids = ids[:limit]

    processed = 0
    for image_id in ids:
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
            "id": image_id, "file": pred["file"], "true_class": true_cls,
            "top1": top1, "top2": top3[1] if len(top3) > 1 else "", "top3": top3[2] if len(top3) > 2 else "",
            "conf1": pred["confidence"], "ok_top1": str(ok1).lower(), "ok_top3": str(ok3).lower(),
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
            "id": image_id, "file": pred["file"], "true_class": true_cls,
            "top1": top1, "top3": top3, "confidence": pred["confidence"],
            "ok_top1": ok1, "ok_top3": ok3, "feature_tags": pred["feature_tags"],
            "features": pred["features"], "draw_priority": pred["draw_priority"],
            "layer_order": pred["layer_order"], "ranked": pred["ranked"],
        })
        processed += 1
        if processed % 25 == 0:
            print(f"processed {processed}/{len(ids)}")

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
    summary = {
        "dataset": str(dataset), "run_dir": str(run_dir), "total": total,
        "top1_correct": top1_total, "top3_correct": top3_total,
        "top1_accuracy": round(safe_div(top1_total, total), 5),
        "top3_accuracy": round(safe_div(top3_total, total), 5),
        "classes": classes, "per_class": per_class_summary,
        "worst_classes": worst_classes, "common_confusions": common_confusions,
        "outputs": {
            "benchmark_results_csv": str(results_csv), "mistakes_csv": str(mistakes_csv),
            "predictions_jsonl": str(predictions_jsonl), "benchmark_summary_json": str(summary_json),
            "report_md": str(report_md),
        },
        "note": "Analyzer predictions used image pixels only. Labels were used only by benchmark comparison.",
    }
    summary_json.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = ["# SolumDraw Benchmark Report", "", f"- Dataset: `{dataset}`", f"- Total images: `{total}`", f"- Top-1 accuracy: `{summary['top1_accuracy']}`", f"- Top-3 accuracy: `{summary['top3_accuracy']}`", f"- Mistakes: `{len(mistake_rows)}`", "", "## Worst classes"]
    for cls in worst_classes:
        s = per_class_summary[cls]
        lines.append(f"- `{cls}`: top1 `{s['top1_accuracy']}` / total `{s['total']}`")
    lines.append("")
    lines.append("## Common confusions")
    for c in common_confusions[:15]:
        lines.append(f"- `{c['true']}` -> `{c['pred']}`: `{c['count']}`")
    lines.append("")
    lines.append("## Output files")
    for k, v in summary["outputs"].items():
        lines.append(f"- `{k}`: `{v}`")
    report_md.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("=== BENCHMARK DONE ===")
    print("dataset:", dataset)
    print("run_dir:", run_dir)
    print("total:", total)
    print("top1_accuracy:", summary["top1_accuracy"])
    print("top3_accuracy:", summary["top3_accuracy"])
    print("results_csv:", results_csv)
    print("mistakes_csv:", mistakes_csv)
    print("summary_json:", summary_json)
    return run_dir


def render_single_html(image_path: Path, analysis: Dict[str, Any], out_html: Path) -> None:
    mime = "image/png" if image_path.suffix.lower() == ".png" else "image/jpeg"
    b64 = base64.b64encode(image_path.read_bytes()).decode("ascii")
    rows = "\n".join(f"<tr><td>{r['class']}</td><td>{r['score']}</td><td>{r['confidence']}</td></tr>" for r in analysis["ranked"][:8])
    features_rows = "\n".join(f"<tr><td>{k}</td><td>{v}</td></tr>" for k, v in analysis["features"].items())
    html = f"""<!doctype html><html lang="ru"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/><title>SolumDraw Single Analysis</title><style>body{{font-family:system-ui,Arial;background:#101114;color:#eee;margin:0;padding:16px;}}main{{max-width:760px;margin:auto;}}.card{{background:#181a20;border:1px solid #2b2f38;border-radius:14px;padding:14px;margin:12px 0;}}img{{max-width:100%;border-radius:12px;border:1px solid #333;}}table{{width:100%;border-collapse:collapse;font-size:13px;}}td,th{{border-bottom:1px solid #2b2f38;padding:7px;text-align:left;vertical-align:top;}}.badge{{display:inline-block;background:#263248;color:#9ec1ff;border-radius:999px;padding:5px 9px;margin:3px;font-size:12px;}}.big{{font-size:22px;font-weight:800;}}</style></head><body><main><h1>SolumDraw Single Analysis</h1><div class="card"><img src="data:{mime};base64,{b64}"/></div><div class="card"><div class="big">Top-1: {analysis['top1']}</div><p>Top-3: {", ".join(analysis['top3'])}</p></div><div class="card"><h2>Feature tags</h2>{''.join(f'<span class="badge">{x}</span>' for x in analysis['feature_tags'])}</div><div class="card"><h2>Draw priority</h2><ol>{''.join(f'<li>{x}</li>' for x in analysis['draw_priority'])}</ol></div><div class="card"><h2>Layer order</h2><ol>{''.join(f'<li>{x}</li>' for x in analysis['layer_order'])}</ol></div><div class="card"><h2>Ranked classes</h2><table><tr><th>class</th><th>score</th><th>confidence</th></tr>{rows}</table></div><div class="card"><h2>Raw features</h2><table>{features_rows}</table></div></main></body></html>"""
    out_html.write_text(html, encoding="utf-8")


def run_single(image: Path, out: str | None = None, html: bool = False) -> Path:
    image = image.expanduser().resolve()
    out_dir = Path(out).expanduser() if out else Path.cwd() / f"single_analysis_{_dt.datetime.now().strftime('%Y%m%d_%H%M%S')}"
    out_dir.mkdir(parents=True, exist_ok=True)
    analysis = analyze_image(image, classes=DEFAULT_CLASSES)
    analysis["source_image"] = str(image)
    out_json = out_dir / "single_analysis.json"
    out_json.write_text(json.dumps(analysis, ensure_ascii=False, indent=2), encoding="utf-8")
    if html:
        render_single_html(image, analysis, out_dir / "single_analysis.html")
    print("=== SINGLE ANALYSIS DONE ===")
    print("image:", image)
    print("top1:", analysis["top1"])
    print("top3:", ", ".join(analysis["top3"]))
    print("json:", out_json)
    if html:
        print("html:", out_dir / "single_analysis.html")
    return out_dir


def main() -> None:
    ap = argparse.ArgumentParser(description="SolumDraw Analyzer + Benchmark v1")
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
