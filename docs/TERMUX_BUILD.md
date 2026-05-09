# Termux build

SolumDraw currently targets Android SDK 34 because this is the stable SDK in the phone/Termux workflow.

## Build APK

```bash
cd ~/SolumDraw && \
git fetch origin && \
git checkout patch-01-clean-foundation && \
git pull --ff-only origin patch-01-clean-foundation && \
gradle assembleDebug -Dandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 && \
mkdir -p /storage/emulated/0/Download && \
cp app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/Download/SolumDraw_patch01.apk && \
echo "DONE: /storage/emulated/0/Download/SolumDraw_patch01.apk"
```

Expected output:

```text
/storage/emulated/0/Download/SolumDraw_patch01.apk
```

## Compatibility note

If Android SDK 35 exists but `aapt2` fails with `failed to load include path ... android-35/android.jar`, keep `compileSdk = 34` and `targetSdk = 34` until the SDK 35 platform is repaired.
