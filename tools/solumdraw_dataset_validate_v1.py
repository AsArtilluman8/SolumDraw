#!/usr/bin/env python3
"""
SolumDraw dataset validator v1.1.

Input:
  /storage/emulated/0/Download/SolumDrawDatasets/gallery_v1/metadata.jsonl

Output next to metadata:
  validation.jsonl
  validation_report.html
  analyzer_manifest.tsv

Goal:
  keep the dataset honest and make the HTML report self-contained.
"""
import argparse, base64, json, mimetypes
from pathlib import Path

EXPECTED = {
    'anime_character': ['anime','manga','character','girl','boy','waifu','chibi'],
    'portrait_photo': ['portrait','face','headshot','woman','man','person'],
    'nature_scene': ['forest','nature','landscape','mountain','river','tree','valley'],
    'cinematic_scene': ['cinematic','movie','dramatic','scene','film','fantasy'],
    'ui_design': ['ui','interface','dashboard','app','screen','button','card'],
    'logo_icon': ['logo','icon','symbol','emblem','mark'],
    'vector_flat': ['vector','flat','minimal','geometric','shape'],
    'sketch_lineart': ['sketch','line art','pencil','ink','drawing'],
    'product_photo': ['product','studio','packshot','object'],
    '3d_render': ['3d','render','cgi','blender','octane']
}

PLACEHOLDER_HOSTS = ['picsum.photos','placehold.co','placeholder.com','loremflickr.com']
MIN_BYTES = 8000
MAX_EMBED_BYTES = 900_000


def load_jsonl(path):
    for i, line in enumerate(Path(path).read_text('utf-8', errors='replace').splitlines(), 1):
        if not line.strip():
            continue
        try:
            yield json.loads(line)
        except Exception as e:
            yield {'_error': str(e), '_line': i}


def score_prompt(genre, prompt):
    p = (prompt or '').lower()
    keys = EXPECTED.get(genre, [])
    hits = [k for k in keys if k in p]
    return len(hits), hits


def validate_record(r):
    flags = []
    genre = r.get('genre','unknown')
    declared = r.get('declared_genre','')
    prompt = r.get('prompt','')
    url = r.get('url','')
    file_path = r.get('file','')
    size = int(r.get('bytes',0) or 0)
    hits, hit_words = score_prompt(genre, prompt)
    if not prompt or len(prompt.strip()) < 12:
        flags.append('missing_or_weak_prompt')
    if hits == 0 and genre != 'unknown':
        flags.append('prompt_does_not_support_genre')
    if declared and declared != genre:
        flags.append('declared_vs_inferred_genre_changed')
    if any(h in url.lower() for h in PLACEHOLDER_HOSTS):
        flags.append('placeholder_or_random_source')
    if size < MIN_BYTES:
        flags.append('small_file')
    if not file_path or not Path(file_path).exists():
        flags.append('missing_local_file')
    risk = 'low'
    if len(flags) >= 3: risk = 'high'
    elif len(flags) >= 1: risk = 'medium'
    return {**r, 'validation': {'risk': risk, 'flags': flags, 'prompt_hits': hit_words}}


def image_data_uri(path):
    if not path:
        return ''
    p = Path(path)
    if not p.exists() or not p.is_file():
        return ''
    try:
        if p.stat().st_size > MAX_EMBED_BYTES:
            return ''
        mime = mimetypes.guess_type(str(p))[0] or 'image/jpeg'
        data = base64.b64encode(p.read_bytes()).decode('ascii')
        return f'data:{mime};base64,{data}'
    except Exception:
        return ''


def write_html(root, rows):
    def esc(s):
        import html
        return html.escape(str(s or ''))
    trs = []
    for r in rows:
        v = r['validation']
        uri = image_data_uri(r.get('file',''))
        img = f"<img src='{uri}'>" if uri else '<span class=bad>no preview</span>'
        trs.append(f"<tr class='{esc(v['risk'])}'><td>{esc(v['risk'])}</td><td>{esc(r.get('genre'))}</td><td>{img}</td><td>{esc(', '.join(v['flags']))}</td><td>{esc(r.get('prompt'))}</td><td>{esc(r.get('url'))}</td></tr>")
    css = "body{font-family:monospace;background:#080808;color:#eee;padding:14px}img{width:160px;border-radius:8px}td,th{border:1px solid #333;padding:6px;vertical-align:top}table{border-collapse:collapse;width:100%}.high{background:#361010}.medium{background:#332a10}.low{background:#102414}.bad{color:#ff7070}"
    text = f"<!doctype html><meta charset='utf-8'><style>{css}</style><h1>SolumDraw Dataset Validation</h1><p>items={len(rows)} / embedded base64 previews</p><table><tr><th>risk</th><th>genre</th><th>image</th><th>flags</th><th>prompt</th><th>url</th></tr>{''.join(trs)}</table>"
    (root/'validation_report.html').write_text(text, 'utf-8')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('metadata', nargs='?', default='/storage/emulated/0/Download/SolumDrawDatasets/gallery_v1/metadata.jsonl')
    args = ap.parse_args()
    meta = Path(args.metadata)
    root = meta.parent
    rows = [validate_record(r) for r in load_jsonl(meta) if '_error' not in r]
    with (root/'validation.jsonl').open('w', encoding='utf-8') as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False)+'\n')
    with (root/'analyzer_manifest.tsv').open('w', encoding='utf-8') as f:
        f.write('# file\texpected_genre\tprompt\trisk\tflags\n')
        for r in rows:
            v = r['validation']
            prompt = r.get('prompt','').replace('\t',' ')
            f.write(f"{r.get('file','')}\t{r.get('genre','')}\t{prompt}\t{v['risk']}\t{','.join(v['flags'])}\n")
    write_html(root, rows)
    summary = {k: sum(1 for r in rows if r['validation']['risk']==k) for k in ['low','medium','high']}
    print('DONE')
    print('ROOT:', root)
    print('VALIDATION:', root/'validation.jsonl')
    print('REPORT:', root/'validation_report.html')
    print('ANALYZER_MANIFEST:', root/'analyzer_manifest.tsv')
    print('SUMMARY:', summary)

if __name__ == '__main__':
    main()
