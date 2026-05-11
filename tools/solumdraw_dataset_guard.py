#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
from pathlib import Path

ALLOWED = {'.jpg', '.jpeg', '.png', '.webp'}
BLOCKED = {'.mp4', '.webm', '.gif', '.mov', '.m4v', '.mkv', '.avi', '.zip', '.rar', '.7z'}
PNG_SIG = b'\x89PNG\r\n\x1a\n'
JPG_SIG = b'\xff\xd8\xff'
WEBP_MARK = b'WEBP'


def is_valid_image_header(path: Path) -> bool:
    try:
        data = path.read_bytes()[:16]
    except Exception:
        return False
    if path.suffix.lower() in ('.jpg', '.jpeg'):
        return data.startswith(JPG_SIG)
    if path.suffix.lower() == '.png':
        return data.startswith(PNG_SIG)
    if path.suffix.lower() == '.webp':
        return len(data) >= 12 and data[:4] == b'RIFF' and data[8:12] == WEBP_MARK
    return False


def sha1_head(path: Path, limit: int = 1024 * 1024) -> str:
    h = hashlib.sha1()
    with path.open('rb') as f:
        h.update(f.read(limit))
    return h.hexdigest()


def scan(root: Path, max_mb: float, move_bad: bool):
    max_bytes = int(max_mb * 1024 * 1024)
    rejected = root / 'rejected'
    duplicates = root / 'duplicates'
    if move_bad:
        rejected.mkdir(exist_ok=True)
        duplicates.mkdir(exist_ok=True)

    stats = {
        'root': str(root),
        'allowed': 0,
        'blocked': 0,
        'too_large': 0,
        'too_small': 0,
        'bad_header': 0,
        'duplicates': 0,
        'total_files': 0,
        'kept_files': [],
        'rejected_files': [],
        'duplicate_files': []
    }
    seen = {}

    for path in sorted(root.rglob('*')):
        if not path.is_file():
            continue
        if 'rejected' in path.parts or 'duplicates' in path.parts:
            continue
        stats['total_files'] += 1
        ext = path.suffix.lower()
        size = path.stat().st_size
        reason = None
        if ext in BLOCKED:
            stats['blocked'] += 1
            reason = 'blocked_extension'
        elif ext not in ALLOWED:
            stats['blocked'] += 1
            reason = 'unknown_extension'
        elif size > max_bytes:
            stats['too_large'] += 1
            reason = 'too_large'
        elif size < 4096:
            stats['too_small'] += 1
            reason = 'too_small'
        elif not is_valid_image_header(path):
            stats['bad_header'] += 1
            reason = 'bad_image_header'

        if reason:
            stats['rejected_files'].append({'path': str(path), 'reason': reason, 'size': size})
            if move_bad:
                target = rejected / path.name
                try:
                    path.replace(target)
                except Exception:
                    pass
            continue

        key = (size, sha1_head(path))
        if key in seen:
            stats['duplicates'] += 1
            stats['duplicate_files'].append({'path': str(path), 'duplicate_of': seen[key], 'size': size})
            if move_bad:
                target = duplicates / path.name
                try:
                    path.replace(target)
                except Exception:
                    pass
            continue
        seen[key] = str(path)
        stats['allowed'] += 1
        stats['kept_files'].append({'path': str(path), 'size': size})

    return stats


def main():
    p = argparse.ArgumentParser(description='SolumDraw dataset guard: images only, size limits, duplicates, broken headers.')
    p.add_argument('--root', default='datasets/SolumDrawDataset_v2', help='Dataset or incoming folder to scan')
    p.add_argument('--max-mb', type=float, default=4.0, help='Max allowed image size in MB')
    p.add_argument('--move-bad', action='store_true', help='Move rejected files to rejected/ and duplicates/')
    p.add_argument('--out', default='', help='Output JSON path; default: <root>/dataset_guard_report.json')
    args = p.parse_args()

    root = Path(args.root).expanduser().resolve()
    root.mkdir(parents=True, exist_ok=True)
    stats = scan(root, args.max_mb, args.move_bad)
    out = Path(args.out).expanduser() if args.out else root / 'dataset_guard_report.json'
    out.write_text(json.dumps(stats, ensure_ascii=False, indent=2), encoding='utf-8')

    print('SOLUMDRAW_DATASET_GUARD')
    print('ROOT=' + str(root))
    print('TOTAL=' + str(stats['total_files']))
    print('KEPT=' + str(stats['allowed']))
    print('BLOCKED=' + str(stats['blocked']))
    print('TOO_LARGE=' + str(stats['too_large']))
    print('BAD_HEADER=' + str(stats['bad_header']))
    print('DUPLICATES=' + str(stats['duplicates']))
    print('REPORT=' + str(out))


if __name__ == '__main__':
    main()
