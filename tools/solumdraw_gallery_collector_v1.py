#!/usr/bin/env python3
"""
SolumDraw gallery collector v1.

Purpose:
- collect image+prompt pairs into a clean local dataset
- keep metadata honest
- avoid fake labels

Input manifest TSV:
  genre<TAB>image_url<TAB>prompt<TAB>source_url<TAB>model

Output:
  /storage/emulated/0/Download/SolumDrawDatasets/gallery_v1/
    <genre>/*.jpg|png|webp
    metadata.jsonl
    report.html
"""
import argparse
import hashlib
import html
import json
import os
import time
import urllib.request
from pathlib import Path

ROOT = Path('/storage/emulated/0/Download/SolumDrawDatasets/gallery_v1')
IMG_EXTS = ('.jpg', '.jpeg', '.png', '.webp')

GENRE_HINTS = {
    'anime_character': ['anime', 'manga', 'girl', 'boy', 'character', 'waifu'],
    'portrait_photo': ['portrait', 'face', 'person', 'woman', 'man'],
    'cinematic_scene': ['cinematic', 'movie still', 'dark', 'dramatic'],
    'nature_scene': ['forest', 'nature', 'landscape', 'mountain', 'river'],
    'ui_design': ['ui', 'app', 'dashboard', 'interface', 'mobile screen'],
    'logo_icon': ['logo', 'icon', 'symbol', 'emblem'],
    'vector_flat': ['vector', 'flat', 'minimal', 'geometric'],
    'sketch_lineart': ['sketch', 'line art', 'drawing', 'pencil'],
    'product_photo': ['product', 'packshot', 'studio photo'],
    '3d_render': ['3d render', 'octane', 'blender', 'cgi']
}


def safe_name(s: str) -> str:
    return ''.join(c if c.isalnum() or c in '._-' else '_' for c in s)[:80] or 'item'


def infer_genre(prompt: str, fallback: str) -> str:
    p = (prompt or '').lower()
    scores = []
    for genre, keys in GENRE_HINTS.items():
        scores.append((sum(1 for k in keys if k in p), genre))
    scores.sort(reverse=True)
    return scores[0][1] if scores and scores[0][0] > 0 else (fallback or 'unknown')


def download(url: str, out: Path) -> int:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 SolumDrawCollector/1.0'})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = r.read()
    out.write_bytes(data)
    return len(data)


def read_manifest(path: Path):
    for line_no, raw in enumerate(path.read_text('utf-8', errors='replace').splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        parts = line.split('\t')
        if len(parts) < 2:
            yield {'line': line_no, 'error': 'expected at least genre<TAB>image_url'}
            continue
        genre = parts[0].strip()
        url = parts[1].strip()
        prompt = parts[2].strip() if len(parts) > 2 else ''
        source = parts[3].strip() if len(parts) > 3 else ''
        model = parts[4].strip() if len(parts) > 4 else ''
        yield {'line': line_no, 'genre': infer_genre(prompt, genre), 'declared_genre': genre, 'url': url, 'prompt': prompt, 'source': source, 'model': model}


def write_report(root: Path, records, errors):
    rows = []
    for r in records:
        rows.append(f"<tr><td>{html.escape(r['genre'])}</td><td><img src='{html.escape(r['rel'])}'></td><td>{html.escape(r.get('prompt',''))}</td><td>{html.escape(r.get('model',''))}</td></tr>")
    err = ''.join(f"<li>{html.escape(str(e))}</li>" for e in errors)
    text = f"""<!doctype html><meta charset='utf-8'><style>body{{font-family:monospace;background:#080808;color:#eee;padding:14px}}img{{width:180px;border-radius:8px}}td,th{{border:1px solid #333;padding:6px;vertical-align:top}}table{{border-collapse:collapse;width:100%}}</style><h1>SolumDraw Gallery Dataset v1</h1><p>images={len(records)} errors={len(errors)}</p><h2>Errors</h2><ul>{err}</ul><h2>Items</h2><table><tr><th>genre</th><th>image</th><th>prompt</th><th>model</th></tr>{''.join(rows)}</table>"""
    (root / 'report.html').write_text(text, 'utf-8')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('manifest', help='TSV: genre<TAB>image_url<TAB>prompt<TAB>source_url<TAB>model')
    ap.add_argument('--root', default=str(ROOT))
    ap.add_argument('--reset', action='store_true')
    args = ap.parse_args()
    root = Path(args.root)
    if args.reset and root.exists():
        import shutil
        shutil.rmtree(root)
    root.mkdir(parents=True, exist_ok=True)
    meta_path = root / 'metadata.jsonl'
    records, errors = [], []
    for item in read_manifest(Path(args.manifest)):
        if 'error' in item:
            errors.append(item)
            continue
        try:
            ext = os.path.splitext(item['url'].split('?')[0])[1].lower()
            if ext not in IMG_EXTS:
                ext = '.jpg'
            h = hashlib.sha1((item['url'] + item.get('prompt','')).encode()).hexdigest()[:12]
            folder = root / safe_name(item['genre'])
            folder.mkdir(parents=True, exist_ok=True)
            out = folder / f"{safe_name(item['genre'])}_{h}{ext}"
            size = download(item['url'], out)
            rec = dict(item)
            rec.update({'file': str(out), 'rel': str(out.relative_to(root)), 'bytes': size, 'ts': int(time.time())})
            records.append(rec)
            print('[OK]', rec['rel'], size, 'bytes')
        except Exception as e:
            errors.append({'item': item, 'error': str(e)})
            print('[ERR]', item.get('url'), e)
    with meta_path.open('w', encoding='utf-8') as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + '\n')
    (root / 'errors.json').write_text(json.dumps(errors, ensure_ascii=False, indent=2), 'utf-8')
    write_report(root, records, errors)
    print('DONE')
    print('ROOT:', root)
    print('METADATA:', meta_path)
    print('REPORT:', root / 'report.html')
    print('OK:', len(records), 'ERRORS:', len(errors))

if __name__ == '__main__':
    main()
