#!/usr/bin/env python3
"""
SolumDraw MeiGen/public gallery probe v1.

Purpose:
- probe a public page for image URLs and prompt-like text
- write a TSV manifest compatible with solumdraw_gallery_collector_v1.py

This is a probe, not a guaranteed scraper. If a site renders everything via JS
or blocks direct HTML access, the report will say so honestly.
"""
import argparse, json, re, urllib.request
from html import unescape
from pathlib import Path

IMG_RE = re.compile(r'https?://[^"\'<> ]+?\.(?:png|jpg|jpeg|webp)(?:\?[^"\'<> ]*)?', re.I)
META_RE = re.compile(r'<meta[^>]+(?:property|name)=["\']([^"\']+)["\'][^>]+content=["\']([^"\']*)["\'][^>]*>', re.I)
SCRIPT_JSON_RE = re.compile(r'<script[^>]+type=["\']application/ld\+json["\'][^>]*>(.*?)</script>', re.I | re.S)

PROMPT_KEYS = ['prompt', 'description', 'caption', 'alt', 'title']


def fetch(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 SolumDrawProbe/1.0'})
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.read().decode('utf-8', errors='replace')


def clean(s):
    s = re.sub(r'<[^>]+>', ' ', s or '')
    s = unescape(s)
    s = re.sub(r'\s+', ' ', s).strip()
    return s


def pick_prompt(html, metas, json_blocks):
    candidates = []
    for k, v in metas.items():
        lk = k.lower()
        if any(x in lk for x in PROMPT_KEYS):
            candidates.append(v)
    for block in json_blocks:
        try:
            obj = json.loads(block)
            stack = [obj]
            while stack:
                cur = stack.pop()
                if isinstance(cur, dict):
                    for k, v in cur.items():
                        if isinstance(v, str) and any(x in k.lower() for x in PROMPT_KEYS):
                            candidates.append(v)
                        elif isinstance(v, (dict, list)):
                            stack.append(v)
                elif isinstance(cur, list):
                    stack.extend(cur)
        except Exception:
            pass
    for c in candidates:
        c = clean(c)
        if len(c) >= 20:
            return c[:1200]
    title = re.search(r'<title[^>]*>(.*?)</title>', html, re.I | re.S)
    return clean(title.group(1))[:500] if title else ''


def infer_genre(prompt):
    p = (prompt or '').lower()
    if any(x in p for x in ['anime','manga','waifu','character']): return 'anime_character'
    if any(x in p for x in ['portrait','face','headshot']): return 'portrait_photo'
    if any(x in p for x in ['forest','landscape','nature','mountain','river']): return 'nature_scene'
    if any(x in p for x in ['ui','dashboard','app interface','mobile screen']): return 'ui_design'
    if any(x in p for x in ['logo','icon','symbol','emblem']): return 'logo_icon'
    if any(x in p for x in ['vector','flat','minimal geometric']): return 'vector_flat'
    if any(x in p for x in ['sketch','line art','pencil']): return 'sketch_lineart'
    if any(x in p for x in ['cinematic','movie still','dramatic']): return 'cinematic_scene'
    return 'unknown'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('url')
    ap.add_argument('--out', default='tools/meigen_probe_manifest.tsv')
    args = ap.parse_args()
    html_text = fetch(args.url)
    metas = {m.group(1): unescape(m.group(2)) for m in META_RE.finditer(html_text)}
    json_blocks = [m.group(1).strip() for m in SCRIPT_JSON_RE.finditer(html_text)]
    imgs = []
    for v in list(metas.values()) + IMG_RE.findall(html_text):
        if isinstance(v, str) and re.search(r'\.(png|jpg|jpeg|webp)', v, re.I):
            if v not in imgs:
                imgs.append(v)
    prompt = pick_prompt(html_text, metas, json_blocks)
    genre = infer_genre(prompt)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open('w', encoding='utf-8') as f:
        f.write('# genre\timage_url\tprompt\tsource_url\tmodel\n')
        for img in imgs[:25]:
            f.write(f'{genre}\t{img}\t{prompt.replace(chr(9), " ")}\t{args.url}\tunknown\n')
    report = {
        'url': args.url,
        'image_candidates': len(imgs),
        'prompt_found': bool(prompt),
        'prompt_preview': prompt[:240],
        'genre': genre,
        'out': str(out),
        'note': 'If image_candidates=0 or prompt_found=false, the page likely needs JS/API probing.'
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
