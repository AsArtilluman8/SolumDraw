#!/usr/bin/env python3
import json
import os
import shutil
import subprocess
from pathlib import Path


def run(cmd):
    try:
        p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=12)
        return {'cmd': cmd, 'ok': p.returncode == 0, 'code': p.returncode, 'stdout': p.stdout.strip(), 'stderr': p.stderr.strip()}
    except Exception as e:
        return {'cmd': cmd, 'ok': False, 'code': -1, 'stdout': '', 'stderr': str(e)}


def exists_any(paths):
    return [str(p) for p in paths if p.exists()]


def main():
    home = Path.home()
    repo = Path.cwd()
    candidates = {
        'opencv_mobile_dirs': exists_any([
            repo / 'third_party' / 'opencv-mobile',
            repo / 'opencv-mobile',
            home / 'opencv-mobile',
            home / 'third_party' / 'opencv-mobile'
        ]),
        'opencv_android_sdk_dirs': exists_any([
            repo / 'OpenCV-android-sdk',
            home / 'OpenCV-android-sdk',
            home / 'opencv' / 'OpenCV-android-sdk'
        ]),
        'native_libs': exists_any(list((repo / 'app' / 'src' / 'main' / 'jniLibs').glob('**/libopencv*.so')) if (repo / 'app' / 'src' / 'main' / 'jniLibs').exists() else []),
        'java_wrappers': exists_any(list((repo / 'app' / 'src' / 'main' / 'java').glob('**/org/opencv/**/*.java')) if (repo / 'app' / 'src' / 'main' / 'java').exists() else [])
    }
    tools = {
        'gradle': shutil.which('gradle'),
        'python3': shutil.which('python3'),
        'cmake': shutil.which('cmake'),
        'clang': shutil.which('clang'),
        'pkg-config': shutil.which('pkg-config')
    }
    checks = {
        'gradle_projects': run(['gradle', 'projects']) if tools['gradle'] else {'ok': False, 'stderr': 'gradle not found'},
        'pkg_config_opencv4': run(['pkg-config', '--modversion', 'opencv4']) if tools['pkg-config'] else {'ok': False, 'stderr': 'pkg-config not found'}
    }
    result = {
        'status': 'opencv_probe_only_no_project_changes',
        'repo': str(repo),
        'tools': tools,
        'candidates': candidates,
        'checks': checks,
        'recommendation': recommendation(candidates, checks)
    }
    out = repo / 'cv_probe_report.json'
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print('SOLUMDRAW_CV_PROBE')
    print('REPORT=' + str(out))
    print('RECOMMENDATION=' + result['recommendation'])


def recommendation(candidates, checks):
    if candidates['native_libs'] and candidates['java_wrappers']:
        return 'OpenCV files already present. Next patch can wire Android OpenCV backend.'
    if candidates['opencv_mobile_dirs']:
        return 'opencv-mobile directory found. Next patch can add JNI/backend integration carefully.'
    if candidates['opencv_android_sdk_dirs']:
        return 'OpenCV Android SDK found. Check APK size before integration.'
    if checks.get('pkg_config_opencv4', {}).get('ok'):
        return 'Termux OpenCV package visible for tools, but Android APK still needs separate runtime integration.'
    return 'No OpenCV found. Keep CPU fallback now; download/build opencv-mobile before native integration.'


if __name__ == '__main__':
    main()
