# Termux build

Use this after checking out a branch locally.

```bash
cd ~/SolumDraw && \
gradle assembleDebug -Dandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 && \
mkdir -p /storage/emulated/0/Download && \
cp app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/Download/SolumDraw_patch01.apk && \
echo "DONE: /storage/emulated/0/Download/SolumDraw_patch01.apk"
```

Expected output:

```text
/storage/emulated/0/Download/SolumDraw_patch01.apk
```
