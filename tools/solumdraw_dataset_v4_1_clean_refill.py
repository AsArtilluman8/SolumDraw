#!/usr/bin/env python3
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path('/storage/emulated/0/Download/SolumDrawTestImages')
EXPECTED = ROOT / 'expected.json'
REPORT = ROOT / 'source_report.json'
REJECTED = ROOT / 'rejected.json'

TARGET_PER_GENRE = 12
IMAGE_SIZE = 'Medium'

STRICT_REFILL_GENRES = {
    'photo_dark': [
        'low key portrait photography no text',
        'dark moody street photography no text',
        'cinematic dark photo scene no text',
        'low light cinematic photography no typography',
    ],
    'photo_object': [
        'single object product photography isolated no text',
        'one object on plain background photography no text',
        'single item product photo no text',
        'minimal still life object photography no text',
    ],
    'ui_or_text_heavy': [
        'mobile app interface screenshot ui screen no mockup',
        'real app dashboard screenshot mobile ui',
        'website dashboard interface screenshot',
        'mobile app settings screen screenshot',
    ],
    'text_heavy': [
        'book page text close up scan',
        'document page text scan',
        'magazine page text layout scan',
        'newspaper page text close up',
    ],
    'anime_cartoon_flat': [
        'anime character illustration flat colors no text',
        'cartoon character flat color illustration no text',
        'anime style character art clean flat colors',
    ],
    'sketch_lineart': [
        'pencil sketch line art drawing no text',
        'black white lineart character drawing',
        'ink sketch line art no text',
    ],
    'digital_art_wallpaper': [
        'digital art wallpaper fantasy no text',
        'sci fi digital art wallpaper no text',
        'abstract digital art wallpaper no text',
    ],
    'vector_flat_art': [
        'flat vector illustration geometric art no text',
        'minimal vector illustration colorful no text',
        'flat design illustration no text',
    ],
    'logo_icon_flat': [
        'flat app icon png transparent',
        'minimal logo icon flat design png',
        'vector app icon flat png',
    ],
}

# Query/source/title reject terms by genre. This is not image analysis cheating: it rejects obvious wrong search results.
COMMON_REJECT_TERMS = [
    'preset', 'presets', 'lightroom', 'lr preset', 'premium collection', 'bundle', 'template',
    'mockup template', 'stock vector collection', 'pack of', 'sale', 'coupon', 'download now',
    'poster template', 'flyer template', 'thumbnail template', 'banner template', 'course', 'tutorial',
]
GENRE_REJECT_TERMS = {
    'photo_dark': COMMON_REJECT_TERMS + ['dark cinematic premium', 'collection', 'before after'],
    'photo_object': COMMON_REJECT_TERMS + ['collage', 'grid', 'set of', 'collection'],
    'ui_or_text_heavy': ['lightroom', 'preset', 'stock photo', 'wallpaper', 'anime', 'cartoon'],
    'text_heavy': ['lightroom', 'preset', 'wallpaper', 'anime', 'cartoon'],
    'anime_cartoon_flat': ['real photo', 'cosplay', 'live action', 'lightroom', 'preset'],
    'sketch_lineart': ['tattoo', 'coloring book collection', 'pack', 'premium'],
    'digital_art_wallpaper': ['lightroom preset', 'template', 'bundle', 'logo'],
    'vector_flat_art': ['photo', 'photography', 'lightroom', 'preset'],
    'logo_icon_flat': ['lightroom', 'photo', 'mockup', 'collection'],
}

IMAGE_EXTS = ['.jpg', '.jpeg', '.png', '.webp', '.bmp']


def ensure_root():
    ROOT.mkdir(parents=True, exist_ok=True)


def read_json(path, fallback):
    if not path.exists():
        return fallback
    try:
        return json.loads(path.read_text(encoding='utf-8'))
    except Exception:
        return fallback


def write_json(path, obj):
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding='utf-8')


def rel(path):
    return path.relative_to(ROOT).as_posix()


def safe_name(s):
    return re.sub(r'[^A-Za-z0-9._-]+', '_', s).strip('._') or 'file'


def request(url, timeout=40):
    req = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36',
        'Accept': '*/*',
    })
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def get_vqd(query):
    url = 'https://duckduckgo.com/?' + urllib.parse.urlencode({'q': query, 'iax': 'images', 'ia': 'images'})
    html = request(url).decode('utf-8', 'ignore')
    for p in [r'vqd="([^"]+)"', r"vqd='([^']+)'", r'vqd=([0-9-]+)&', r'"vqd":"([^"]+)"']:
        m = re.search(p, html)
        if m:
            return m.group(1)
    raise RuntimeError('DuckDuckGo vqd not found')


def ddg_images(query, max_results=90):
    vqd = get_vqd(query)
    params = {'l': 'us-en', 'o': 'json', 'q': query, 'vqd': vqd, 'f': f',,,size:{IMAGE_SIZE},,,', 'p': '1'}
    data = request('https://duckduckgo.com/i.js?' + urllib.parse.urlencode(params)).decode('utf-8', 'ignore')
    js = json.loads(data)
    out = []
    for item in js.get('results', []):
        img = item.get('image')
        if img:
            out.append({
                'image': img,
                'thumbnail': item.get('thumbnail', ''),
                'title': item.get('title', ''),
                'url': item.get('url', ''),
                'source': item.get('source', ''),
            })
        if len(out) >= max_results:
            break
    return out


def text_has_reject(genre, item):
    text = ' '.join([str(item.get('title', '')), str(item.get('url', '')), str(item.get('source', ''))]).lower()
    for term in GENRE_REJECT_TERMS.get(genre, COMMON_REJECT_TERMS):
        if term.lower() in text:
            return True, term
    return False, ''


def ext_from_url(url):
    path = urllib.parse.urlparse(url).path.lower()
    for e in IMAGE_EXTS:
        if path.endswith(e):
            return '.jpg' if e == '.jpeg' else e
    return '.jpg'


def install_pillow_if_needed():
    try:
        from PIL import Image  # noqa
        return
    except Exception:
        print('[SETUP] Installing Pillow...')
        subprocess.run([sys.executable, '-m', 'pip', 'install', '--user', 'pillow'], check=False)


def validate_image(path, genre):
    from PIL import Image, ImageStat
    im = Image.open(path)
    im.verify()
    im = Image.open(path).convert('RGB')
    w, h = im.size
    if w < 180 or h < 180:
        return False, f'too small {w}x{h}'
    if w > 4096 or h > 4096:
        return False, f'too large {w}x{h}'

    # Simple anti-junk checks, not genre classifier shortcuts.
    small = im.resize((96, 96))
    stat = ImageStat.Stat(small)
    mean = stat.mean
    extrema = stat.extrema
    dynamic = sum((hi - lo) for lo, hi in extrema) / 3.0
    if dynamic < 12:
        return False, 'nearly blank/flat image'

    if genre.startswith('photo_'):
        # Reject huge white margins/collages/promo sheets where dominant white is extreme.
        pixels = list(small.getdata())
        white = sum(1 for r, g, b in pixels if r > 245 and g > 245 and b > 245) / len(pixels)
        if white > 0.62:
            return False, 'too much white margin for photo category'

    return True, f'{w}x{h}'


def download_one(url, out_path, genre):
    data = request(url)
    if len(data) < 4000:
        raise RuntimeError(f'too small bytes={len(data)}')
    out_path.write_bytes(data)
    ok, info = validate_image(out_path, genre)
    if not ok:
        out_path.unlink(missing_ok=True)
        raise RuntimeError(info)
    return len(data), info


def rebuild_expected_from_files():
    expected = {}
    for genre_dir in ROOT.iterdir() if ROOT.exists() else []:
        if not genre_dir.is_dir():
            continue
        genre = genre_dir.name
        for f in genre_dir.iterdir():
            if f.is_file() and f.suffix.lower() in IMAGE_EXTS:
                expected[rel(f)] = genre
    return expected


def remove_bad_existing(clean_genres):
    expected = read_json(EXPECTED, {})
    report = read_json(REPORT, {})
    rejected = read_json(REJECTED, {'removed': [], 'download_rejected': []})

    for genre in clean_genres:
        gdir = ROOT / genre
        if not gdir.exists():
            continue
        for f in list(gdir.iterdir()):
            if not f.is_file() or f.suffix.lower() not in IMAGE_EXTS:
                continue
            # We cannot know title for old files unless source_report has it, so keep old images by default.
            # Remove only files that were clearly from old loose generators by suspicious filename/query metadata.
            r = rel(f)
            meta_text = json.dumps(report, ensure_ascii=False).lower()
            reason = None
            if genre == 'photo_dark' and ('preset' in f.name.lower() or 'lightroom' in f.name.lower()):
                reason = 'suspicious filename'
            if reason:
                f.unlink(missing_ok=True)
                expected.pop(r, None)
                rejected['removed'].append({'file': r, 'reason': reason})

    write_json(EXPECTED, rebuild_expected_from_files())
    write_json(REJECTED, rejected)


def count_genre(genre):
    gdir = ROOT / genre
    if not gdir.exists():
        return 0
    return len([f for f in gdir.iterdir() if f.is_file() and f.suffix.lower() in IMAGE_EXTS])


def refill_genre(genre, target=TARGET_PER_GENRE):
    expected = read_json(EXPECTED, {})
    report = read_json(REPORT, {})
    rejected = read_json(REJECTED, {'removed': [], 'download_rejected': []})
    report.setdefault('v4_1_refill', {})
    report['v4_1_refill'].setdefault(genre, {'queries': STRICT_REFILL_GENRES.get(genre, []), 'files': [], 'errors': []})

    gdir = ROOT / genre
    gdir.mkdir(parents=True, exist_ok=True)
    current = count_genre(genre)
    print(f'[GENRE] {genre}: current={current}, target={target}')
    if current >= target:
        return

    for query in STRICT_REFILL_GENRES.get(genre, []):
        if current >= target:
            break
        print(f'[SEARCH] {genre}: {query}')
        try:
            results = ddg_images(query, max_results=90)
        except Exception as e:
            report['v4_1_refill'][genre]['errors'].append({'query': query, 'error': str(e)})
            print('[ERR search]', e)
            continue

        for item in results:
            if current >= target:
                break
            bad, term = text_has_reject(genre, item)
            if bad:
                rejected['download_rejected'].append({'genre': genre, 'query': query, 'reason': 'reject_term:' + term, 'title': item.get('title', ''), 'url': item.get('url', ''), 'image': item.get('image', '')})
                continue

            img_url = item.get('image', '')
            ext = ext_from_url(img_url)
            fname = f'{genre}_{current + 1:03d}{ext}'
            out = gdir / fname
            try:
                size, info = download_one(img_url, out, genre)
                r = rel(out)
                expected[r] = genre
                current += 1
                entry = {'file': r, 'bytes': size, 'info': info, 'query': query, 'title': item.get('title', ''), 'source': item.get('source', ''), 'page_url': item.get('url', ''), 'image_url': img_url}
                report['v4_1_refill'][genre]['files'].append(entry)
                print('[OK]', r, info)
            except Exception as e:
                rejected['download_rejected'].append({'genre': genre, 'query': query, 'reason': str(e), 'title': item.get('title', ''), 'url': item.get('url', ''), 'image': img_url})

    write_json(EXPECTED, rebuild_expected_from_files())
    write_json(REPORT, report)
    write_json(REJECTED, rejected)
    print(f'[DONE] {genre}: {count_genre(genre)}/{target}')


def main():
    install_pillow_if_needed()
    ensure_root()

    # Clean/refill only the categories that were observed as weak/noisy.
    clean_genres = ['photo_dark', 'photo_object', 'ui_or_text_heavy', 'text_heavy']
    refill_genres = ['photo_dark', 'photo_object', 'ui_or_text_heavy', 'text_heavy', 'anime_cartoon_flat', 'sketch_lineart', 'digital_art_wallpaper', 'vector_flat_art', 'logo_icon_flat']

    remove_bad_existing(clean_genres)
    for genre in refill_genres:
        refill_genre(genre, TARGET_PER_GENRE)

    expected = rebuild_expected_from_files()
    write_json(EXPECTED, expected)
    print('\nDONE v4.1 clean/refill')
    print('ROOT:', ROOT)
    print('TOTAL_IMAGES:', len(expected))
    print('EXPECTED:', EXPECTED)
    print('REPORT:', REPORT)
    print('REJECTED:', REJECTED)
    for genre in sorted({p.split('/')[0] for p in expected}):
        print(genre, count_genre(genre))


if __name__ == '__main__':
    main()
